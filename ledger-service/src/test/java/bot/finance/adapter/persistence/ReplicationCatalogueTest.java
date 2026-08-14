package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.PersistenceAdapterTest;
import java.util.Optional;
import java.util.UUID;
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
@Import(ReplicationCatalogue.class)
class ReplicationCatalogueTest {

    private final String slotName =
            "replication_catalogue_test_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private ReplicationCatalogue replicationCatalogue;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private void createSlot() {
        jdbcTemplate.execute("SELECT pg_create_logical_replication_slot('" + slotName + "', 'pgoutput')");
    }

    /** Emits a WAL record no reader will ever confirm, so the slot's retained log moves off zero. */
    private void growWalPastZero() {
        jdbcTemplate.execute("SELECT pg_logical_emit_message(true, 'test', repeat('x', 1000000))");
    }
}
