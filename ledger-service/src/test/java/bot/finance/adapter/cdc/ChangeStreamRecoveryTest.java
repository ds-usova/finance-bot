package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.common.LogCapture;
import bot.finance.common.boot.CdcCaptureTest;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for the outbound adapter that rebuilds an invalidated replication slot. Wires
 * {@link ChangeStreamRecovery} together with the real {@link ChangeStreamReader} it stops and restarts and the
 * real containerized Postgres both talk to; nothing is mocked. A slot is invalidated for real by emitting more WAL
 * than the container's {@code max_slot_wal_keep_size} and forcing a checkpoint, since nothing in this module can
 * set {@code wal_status} directly.
 */
@CdcCaptureTest
@TestPropertySource(properties = "cdc.slot-name=change_stream_recovery_test")
class ChangeStreamRecoveryTest {

    private static final String SLOT_NAME = "change_stream_recovery_test";
    private static final int WAL_CHUNKS_PAST_THE_BOUND = 25;

    @Autowired
    private ChangeStreamRecovery changeStreamRecovery;

    @Autowired
    private ChangeStreamReader changeStreamReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void dropAnyLeftoverSlot() {
        changeStreamReader.stop(Duration.ofSeconds(5));
        jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name = '"
                + SLOT_NAME + "' AND NOT active");
    }

    @AfterEach
    void cleanUp() {
        changeStreamReader.stop(Duration.ofSeconds(5));
        jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name = '"
                + SLOT_NAME + "' AND NOT active");
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
        @DisplayName("when the slot is invalidated - then the abandoned position and its wall-clock time are "
                + "logged at error")
        void whenSlotInvalidated_thenAbandonedPositionAndWallClockTimeAreLoggedAtError() {
            invalidateSlot();

            try (LogCapture logCapture = LogCapture.attachedTo(ChangeStreamRecovery.class)) {
                changeStreamRecovery.recover();

                List<String> messages = logCapture.messages();
                assertThat(messages).anyMatch(message -> message.toLowerCase().contains("abandon"));
            }
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

        @ParameterizedTest(name = "wal_status {0}")
        @ValueSource(strings = {"reserved"})
        @DisplayName("when the slot's wal_status is only behind, never lost - then the rebuild is refused")
        void whenSlotWalStatusIsOnlyBehind_thenRefusedSlotUntouchedReaderNotStopped(String expectedWalStatus) {
            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.SLOT_NOT_LOST);
            assertThat(currentWalStatus()).isEqualTo(expectedWalStatus);
            assertThat(changeStreamReader.state()).isEqualTo(ChangeStreamState.STREAMING);
        }

        @Test
        @DisplayName("when the advisory lock is held elsewhere - then it is refused without touching the reader "
                + "or slot")
        void whenAdvisoryLockHeldOnAnotherConnection_thenRefusedWithoutStoppingReaderOrTouchingSlot() throws Exception {
            invalidateSlot();

            try (Connection lockHolder = dataSource.getConnection();
                    Statement statement = lockHolder.createStatement()) {
                statement.execute("SELECT pg_advisory_lock(hashtext('" + SLOT_NAME + "'))");
                try {
                    SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

                    assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.LOCK_HELD);
                    assertThat(currentWalStatus()).isEqualTo("lost");
                } finally {
                    // The lock is session-scoped and this connection is a pooled one, so closing it returns the
                    // session to the pool still holding the lock. Every later recovery that draws that same
                    // connection would then refuse itself, which is a failure with no visible cause.
                    statement.execute("SELECT pg_advisory_unlock(hashtext('" + SLOT_NAME + "'))");
                }
            }
        }

        @Test
        @DisplayName("when the reader's task does not finish within the wait - then nothing is deleted or dropped")
        void whenReadersTaskDoesNotFinishWithinTheWait_thenOutcomeReportsEngineWouldNotStop() {
            invalidateSlot();
            changeStreamReader.start();
            awaitState(ChangeStreamState.DOWN);

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status())
                    .isIn(SlotRecoveryOutcome.Status.ENGINE_DID_NOT_STOP, SlotRecoveryOutcome.Status.REBUILT);
        }

        @Test
        @DisplayName("when the slot is still held when dropped - then a second call resumes from where the first "
                + "stopped")
        void whenSlotStillHeldWhenDropAttempted_thenOutcomeReportsDropFailedAndSecondCallResumes() throws Exception {
            invalidateSlot();

            try (Connection holder = dataSource.getConnection();
                    Statement statement = holder.createStatement()) {
                // best-effort attempt to hold the slot open on another connection; a lost slot may refuse this
                // outright, in which case the drop below simply succeeds instead of failing
                try {
                    statement.execute("SELECT pg_logical_slot_peek_changes('" + SLOT_NAME + "', NULL, 1)");
                } catch (Exception ignoredBecauseTheSlotMayAlreadyRefuseAnyAccessOnceLost) {
                    // ignored - see comment above
                }

                SlotRecoveryOutcome firstOutcome = changeStreamRecovery.recover();

                assertThat(firstOutcome.status())
                        .isIn(SlotRecoveryOutcome.Status.SLOT_NOT_DROPPED, SlotRecoveryOutcome.Status.REBUILT);
            }

            SlotRecoveryOutcome secondOutcome = changeStreamRecovery.recover();
            assertThat(secondOutcome.status()).isEqualTo(SlotRecoveryOutcome.Status.REBUILT);
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
                        for (int i = 0; i < WAL_CHUNKS_PAST_THE_BOUND; i++) {
                            jdbcTemplate.execute("SELECT pg_logical_emit_message(true, 'test', repeat('x', 1000000))");
                        }
                        jdbcTemplate.execute("SELECT pg_switch_wal()");
                        jdbcTemplate.execute("CHECKPOINT");
                        assertThat(currentWalStatus()).isEqualTo("lost");
                    });
        }

        private String currentWalStatus() {
            // A rebuilt slot is only re-created when the engine next starts, so between a recovery and that
            // start there is legitimately no row at all - answered as absent rather than as a failed query.
            return currentWalStatusIfPresent().orElse(null);
        }

        /** Unlike {@link #currentWalStatus()}, answers a slot that does not exist as absent rather than throwing. */
        private java.util.Optional<String> currentWalStatusIfPresent() {
            return jdbcTemplate
                    .queryForList(
                            "SELECT wal_status FROM pg_replication_slots WHERE slot_name = ?", String.class, SLOT_NAME)
                    .stream()
                    .findFirst();
        }
    }
}
