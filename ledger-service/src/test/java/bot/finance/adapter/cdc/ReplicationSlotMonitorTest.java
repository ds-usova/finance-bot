package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.ReplicationSlots;
import bot.finance.common.boot.CdcAdapterTest;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration test for the outbound adapter reading {@code pg_replication_slots}. Wires only
 * {@link ReplicationSlotMonitor} and calls its own {@link ReplicationSlotMonitor#readSlot()} directly against the
 * real containerized Postgres; nothing is mocked. The slot itself is created and dropped through raw SQL rather
 * than through {@link ChangeStreamReader}, since the monitor's own point is that it needs no engine.
 *
 * <p>Only what needs that real slot is here. What the monitor records from a retention row is
 * {@link ReplicationSlotMonitorGaugesTest}'s.
 */
@CdcAdapterTest
@TestPropertySource(
        properties = {"cdc.slot-name=replication_slot_monitor_test", "cdc.heartbeat-interval=500ms", "cdc.enabled=false"
        })
class ReplicationSlotMonitorTest {

    private static final String SLOT_NAME = "replication_slot_monitor_test";
    private static final String RETAINED_BYTES_METER = "ledger_cdc_slot_retained_bytes";
    private static final String WAL_STATUS_METER = "ledger_cdc_slot_wal_status";

    @Autowired
    private ReplicationSlotMonitor replicationSlotMonitor;

    @Autowired
    private ChangeStreamReader changeStreamReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void dropSlotIfPresent() {
        changeStreamReader.stop(Duration.ofSeconds(5));
        ReplicationSlots.dropIfUnheld(jdbcTemplate, SLOT_NAME);
    }

    @Nested
    @DisplayName("readSlot()")
    class ReadSlot {

        @Test
        @DisplayName("when no engine in this JVM holds the slot - then it is still read and recorded as present")
        void whenNoEngineHoldsTheSlot_thenItIsStillReadAndRecordedAsPresent() {
            createSlotDirectly();

            replicationSlotMonitor.readSlot();

            assertThat(walStatusGauge().value()).isEqualTo(ReplicationSlotState.RESERVED.ordinal());
        }

        @Test
        @DisplayName("when a slot is holding log back - then the retained bytes gauge carries how much")
        void whenSlotIsHoldingLogBack_thenRetainedBytesGaugeCarriesHowMuch() {
            createSlotDirectly();
            ReplicationSlots.emitOneMegabyteOfWal(jdbcTemplate);

            replicationSlotMonitor.readSlot();

            assertThat(retainedBytesGauge().value()).isGreaterThan(0.0);
        }

        private void createSlotDirectly() {
            ReplicationSlots.create(jdbcTemplate, SLOT_NAME);
        }

        private Gauge retainedBytesGauge() {
            return meterRegistry.find(RETAINED_BYTES_METER).gauge();
        }

        private Gauge walStatusGauge() {
            return meterRegistry.find(WAL_STATUS_METER).gauge();
        }
    }
}
