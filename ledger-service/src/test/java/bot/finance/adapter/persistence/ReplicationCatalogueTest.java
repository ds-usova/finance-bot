package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.common.boot.PersistenceAdapterTest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for the outbound adapter reading Postgres's replication catalogue. A replication slot is not
 * transactional state, so the slice's rolled-back transaction does not remove one - every scenario that creates a
 * slot drops it itself.
 */
@PersistenceAdapterTest
@Import({ReplicationCatalogue.class, Slf4jLoggerFactory.class})
class ReplicationCatalogueTest {

    private final String slotName =
            "replication_catalogue_test_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private ReplicationCatalogue replicationCatalogue;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void dropSlotIfPresent() {
        jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots "
                + "WHERE slot_name = '" + slotName + "' AND NOT active");
    }

    @Nested
    @DisplayName("finding what a slot is retaining")
    class FindSlotRetention {

        @Test
        @DisplayName("when a slot holds log behind it - then its retained bytes and its wal_status are answered")
        void whenSlotHoldsLogBehindIt_thenRetainedBytesAndWalStatusAreAnswered() {
            createSlot();
            growWalPastZero();

            Optional<ReplicationSlotRetention> found = replicationCatalogue.findSlotRetention(slotName);

            assertThat(found).isPresent();
            assertThat(found.get().retainedBytes()).isGreaterThan(0L);
            assertThat(found.get().walStatus()).isEqualTo("reserved");
        }

        @Test
        @DisplayName("when no slot of that name exists - then nothing is answered")
        void whenNoSlotOfThatNameExists_thenNothingIsAnswered() {
            Optional<ReplicationSlotRetention> found = replicationCatalogue.findSlotRetention(slotName);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("when another slot of a different name exists - then that one is never answered")
        void whenAnotherSlotOfADifferentNameExists_thenThatOneIsNeverAnswered() {
            createSlot();

            Optional<ReplicationSlotRetention> found = replicationCatalogue.findSlotRetention(slotName + "_absent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("asking whether a publication exists")
    class PublicationExists {

        @Test
        @DisplayName("when the publication capture streams from is declared - then it is answered as present")
        void whenPublicationCaptureStreamsFromIsDeclared_thenItIsAnsweredAsPresent() {
            assertThat(replicationCatalogue.publicationExists("finance_ledger_cdc"))
                    .isTrue();
        }

        @Test
        @DisplayName("when no publication of that name is declared - then it is answered as absent")
        void whenNoPublicationOfThatNameIsDeclared_thenItIsAnsweredAsAbsent() {
            assertThat(replicationCatalogue.publicationExists("a_publication_nobody_declared"))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("running a rebuild under the slot's advisory lock")
    class UnderSlotLock {

        @Test
        @DisplayName("when nothing else holds the lock - then the sequence runs and its answer comes back")
        void whenNothingElseHoldsTheLock_thenSequenceRunsAndItsAnswerComesBack() {
            Optional<String> answer = replicationCatalogue.underSlotLock(slotName, session -> "ran");

            assertThat(answer).contains("ran");
        }

        @Test
        @DisplayName("when another connection holds the lock - then nothing is answered and the sequence never runs")
        void whenAnotherConnectionHoldsTheLock_thenNothingIsAnsweredAndSequenceNeverRuns() throws Exception {
            try (Connection lockHolder = dataSource.getConnection();
                    Statement statement = lockHolder.createStatement()) {
                statement.execute("SELECT pg_advisory_lock(hashtext('" + slotName + "'))");
                try {
                    Optional<String> answer = replicationCatalogue.underSlotLock(slotName, session -> "ran");

                    assertThat(answer).isEmpty();
                } finally {
                    statement.execute("SELECT pg_advisory_unlock(hashtext('" + slotName + "'))");
                }
            }
        }

        @Test
        @DisplayName("when the sequence has returned - then the lock is released rather than left on the session")
        void whenSequenceHasReturned_thenLockIsReleasedRatherThanLeftOnTheSession() {
            replicationCatalogue.underSlotLock(slotName, session -> "ran");

            assertThat(anyBackendHoldsTheSlotLock()).isFalse();
        }

        @Test
        @DisplayName("when the sequence throws - then the lock is still released")
        void whenSequenceThrows_thenLockIsStillReleased() {
            assertThatThrownBy(() -> replicationCatalogue.underSlotLock(slotName, session -> {
                        throw new IllegalStateException("the sequence gave up");
                    }))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(anyBackendHoldsTheSlotLock()).isFalse();
        }

        @Test
        @DisplayName("when the session reads a slot that exists - then its wal_status and confirmed position come back")
        void whenSessionReadsASlotThatExists_thenWalStatusAndConfirmedPositionComeBack() {
            createSlot();

            Optional<Optional<ReplicationSlotPosition>> found =
                    replicationCatalogue.underSlotLock(slotName, SlotRebuildSession::findSlot);

            assertThat(found).isPresent();
            assertThat(found.get()).isPresent();
            assertThat(found.get().get().walStatus()).isEqualTo("reserved");
        }

        @Test
        @DisplayName("when the session reads a slot that does not exist - then it answers nothing")
        void whenSessionReadsASlotThatDoesNotExist_thenItAnswersNothing() {
            Optional<Optional<ReplicationSlotPosition>> found =
                    replicationCatalogue.underSlotLock(slotName, SlotRebuildSession::findSlot);

            assertThat(found).isPresent();
            assertThat(found.get()).isEmpty();
        }

        @Test
        @DisplayName("when the session drops a slot nothing holds - then it is gone and the drop reports success")
        void whenSessionDropsASlotNothingHolds_thenItIsGoneAndTheDropReportsSuccess() {
            createSlot();

            Optional<Boolean> dropped = replicationCatalogue.underSlotLock(slotName, SlotRebuildSession::dropSlot);

            assertThat(dropped).contains(true);
            assertThat(replicationCatalogue.findSlotRetention(slotName)).isEmpty();
        }

        @Test
        @DisplayName("when the session reads the current position - then a write-ahead log position comes back")
        void whenSessionReadsTheCurrentPosition_thenAWriteAheadLogPositionComesBack() {
            Optional<String> lsn = replicationCatalogue.underSlotLock(slotName, SlotRebuildSession::currentWalLsn);

            assertThat(lsn).isPresent();
            assertThat(lsn.get()).matches("[0-9A-F]+/[0-9A-F]+");
        }

        @Test
        @DisplayName("when the offset table the engine owns is absent - then deleting the stored position succeeds")
        void whenOffsetTableTheEngineOwnsIsAbsent_thenDeletingTheStoredPositionSucceeds() {
            Optional<Boolean> deleted =
                    replicationCatalogue.underSlotLock(slotName, SlotRebuildSession::deleteStoredPosition);

            assertThat(deleted).contains(true);
        }
    }

    /**
     * An advisory lock is re-entrant inside the session that holds it, and the pool hands the same connection
     * back, so a second call taking the lock says nothing about the first having released it. {@code pg_locks} is
     * what answers whether any backend still holds it.
     */
    private boolean anyBackendHoldsTheSlotLock() {
        Long objectId = jdbcTemplate.queryForObject("SELECT hashtext(?)::bigint", Long.class, slotName);
        Integer held = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM pg_locks
                WHERE locktype = 'advisory' AND ((classid::bigint << 32) | objid::bigint) = ?
                """,
                Integer.class,
                objectId);
        return held != null && held > 0;
    }

    private void createSlot() {
        jdbcTemplate.execute("SELECT pg_create_logical_replication_slot('" + slotName + "', 'pgoutput')");
    }

    /** Emits a WAL record no reader will ever confirm, so the slot's retained log moves off zero. */
    private void growWalPastZero() {
        jdbcTemplate.execute("SELECT pg_logical_emit_message(true, 'test', repeat('x', 1000000))");
    }
}
