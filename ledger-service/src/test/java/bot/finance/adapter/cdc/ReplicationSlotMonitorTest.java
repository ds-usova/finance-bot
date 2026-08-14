package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.common.boot.CdcCaptureTest;
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
@CdcCaptureTest
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
        jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name = '"
                + SLOT_NAME + "'");
    }

    @Nested
    @DisplayName("readSlot()")
    class ReadSlot {

        @Test
        @DisplayName("when no engine in this JVM holds the slot - then the same two numbers are still recorded")
        void whenNoEngineHoldsTheSlot_thenSameTwoNumbersAreStillRecorded() {
            createSlotDirectly();

            replicationSlotMonitor.readSlot();

            assertThat(retainedBytesGauge()).isNotNull();
            assertThat(walStatusGauge()).isNotNull();
        }

        @Test
        @DisplayName("when a healthy slot is kept moving by the heartbeat - then retained bytes stays near zero "
                + "after an idle period")
        void whenHeartbeatKeepsSlotMoving_thenRetainedBytesStaysNearZeroAfterAnIdlePeriod() {
            createSlotDirectly();

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                replicationSlotMonitor.readSlot();
                assertThat(retainedBytesGauge()).isNotNull();
                assertThat(retainedBytesGauge().value()).isLessThan(1_000_000.0);
            });
        }

        private void createSlotDirectly() {
            jdbcTemplate.execute("SELECT pg_create_logical_replication_slot('" + SLOT_NAME + "', 'pgoutput')");
        }

        private Gauge retainedBytesGauge() {
            return meterRegistry.find(RETAINED_BYTES_METER).gauge();
        }

        private Gauge walStatusGauge() {
            return meterRegistry.find(WAL_STATUS_METER).gauge();
        }
    }
}
