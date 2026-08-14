package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.common.ReplicationSlots;
import bot.finance.common.boot.CdcAdapterTest;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for the outbound adapter that rebuilds an invalidated replication slot. Wires
 * {@link ChangeStreamRecovery} together with the real {@link ChangeStreamReader} it stops and restarts and the
 * real containerized Postgres both talk to; nothing is mocked. A slot is invalidated for real by emitting more WAL
 * than the container's {@code max_slot_wal_keep_size} and forcing a checkpoint, since nothing in this module can
 * set {@code wal_status} directly.
 *
 * <p>Only what needs that really-invalidated slot is here. The statuses {@code recover()} answers from what its
 * collaborators say are {@link ChangeStreamRecoveryOutcomeTest}'s.
 */
@CdcAdapterTest
@TestPropertySource(
        properties = {"cdc.slot-name=change_stream_recovery_test", "cdc.stream-key=change-stream-recovery-test.cdc"})
class ChangeStreamRecoveryTest {

    private static final String SLOT_NAME = "change_stream_recovery_test";
    private static final int WAL_CHUNKS_PAST_THE_BOUND = 25;

    @Autowired
    private ChangeStreamRecovery changeStreamRecovery;

    @Autowired
    private ChangeStreamReader changeStreamReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void dropAnyLeftoverSlot() {
        stopTheReaderAndDropItsSlot();
    }

    @AfterEach
    void cleanUp() {
        stopTheReaderAndDropItsSlot();
    }

    private void stopTheReaderAndDropItsSlot() {
        changeStreamReader.stop(Duration.ofSeconds(5));
        ReplicationSlots.dropIfUnheld(jdbcTemplate, SLOT_NAME);
    }

    @Nested
    @DisplayName("recover()")
    class Recover {

        @Test
        @DisplayName("when the slot is invalidated and the reader is not streaming - then a new slot starts at "
                + "the current end of the log")
        void whenSlotInvalidatedAndReaderNotStreaming_thenPositionDeletedBeforeSlotDroppedAndNewSlotStartsAtEnd() {
            invalidateSlot();

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.REBUILT);
            assertThat(outcome.abandonedPosition()).isPresent();
            assertThat(outcome.resumedPosition()).isPresent();

            // The rebuilt slot is opened by the engine rather than by the recovery, so it appears once the
            // reader is streaming again - which is also what makes it a slot the service is actually using.
            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            assertThat(currentWalStatus()).isIn("reserved", "extended");
        }

        @Test
        @DisplayName("when there is no slot at all - then one is created with no abandoned position")
        void whenNoSlotAtAll_thenOneIsCreatedAndStreamingStartsAtEndWithNoAbandonedPosition() {
            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.REBUILT);
            assertThat(outcome.abandonedPosition()).isEmpty();
            assertThat(outcome.resumedPosition()).isPresent();

            // The rebuilt slot is created by the reader's own start(), on its own executor - not yet visible
            // the instant recover() returns - so this polls for it rather than asserting once.
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(currentWalStatusIfPresent())
                    .hasValueSatisfying(walStatus -> assertThat(walStatus).isIn("reserved", "extended")));
        }

        @Test
        @DisplayName("when the slot's wal_status is only behind, never lost - then the rebuild is refused")
        void whenSlotWalStatusIsOnlyBehind_thenRefusedSlotUntouchedReaderNotStopped() {
            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.SLOT_NOT_LOST);
            assertThat(currentWalStatus()).isEqualTo("reserved");
            assertThat(changeStreamReader.state()).isEqualTo(ChangeStreamState.STREAMING);
        }

        @Test
        @DisplayName("when captured rows changed while invalidated - then none of them is ever offered")
        void whenCapturedRowsChangedWhileSlotInvalidated_thenNoneOfThoseChangesIsEverOfferedOnceStreamingResumes() {
            invalidateSlot();
            jdbcTemplate.update("UPDATE cdc_heartbeat SET beat_at = now()");

            changeStreamRecovery.recover();
            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            assertThat(currentWalStatus()).isIn("reserved", "extended");
        }

        private void awaitState(ChangeStreamState expected) {
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> assertThat(changeStreamReader.state()).isEqualTo(expected));
        }

        /**
         * {@code max_slot_wal_keep_size} only invalidates a slot once a checkpoint actually recycles segments
         * past that bound, so one burst-then-checkpoint round is not reliably enough: each round writes WAL
         * comfortably past the limit, forces a segment boundary, then checkpoints, polling {@code wal_status}
         * before deciding whether another round is needed.
         */
        private void invalidateSlot() {
            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            changeStreamReader.stop(Duration.ofSeconds(5));

            await().atMost(Duration.ofSeconds(60))
                    .pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        ReplicationSlots.burnWal(jdbcTemplate, WAL_CHUNKS_PAST_THE_BOUND);
                        assertThat(currentWalStatus()).isEqualTo("lost");
                    });
        }

        /**
         * A rebuilt slot is only re-created when the engine next starts, so between a recovery and that start
         * there is legitimately no row at all - answered as {@code null} rather than as a failed query.
         */
        private String currentWalStatus() {
            return currentWalStatusIfPresent().orElse(null);
        }

        private Optional<String> currentWalStatusIfPresent() {
            return ReplicationSlots.walStatus(jdbcTemplate, SLOT_NAME);
        }
    }
}
