package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.persistence.ReplicationCatalogue;
import bot.finance.adapter.persistence.ReplicationSlotRetention;
import bot.finance.common.fixtures.CdcConfigurations;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test for what {@link ReplicationSlotMonitor} records from a retention row. What needs a real slot — that
 * the monitor reads one no engine holds, and that a heartbeat keeps its retention near zero — stays in
 * {@link ReplicationSlotMonitorTest}.
 */
class ReplicationSlotMonitorGaugesTest {

    private static final String SLOT_NAME = "replication_slot_monitor_gauges_test";
    private static final String RETAINED_BYTES_METER = "ledger_cdc_slot_retained_bytes";
    private static final String WAL_STATUS_METER = "ledger_cdc_slot_wal_status";

    private ReplicationCatalogue replicationCatalogue;
    private MeterRegistry meterRegistry;
    private ReplicationSlotMonitor replicationSlotMonitor;

    @BeforeEach
    void wireTheCollaborators() {
        replicationCatalogue = mock(ReplicationCatalogue.class);
        meterRegistry = new SimpleMeterRegistry();

        replicationSlotMonitor = new ReplicationSlotMonitor(
                replicationCatalogue,
                CdcConfigurations.forSlot(SLOT_NAME),
                new ChangeStreamMeters(meterRegistry),
                mock(ScheduledExecutorService.class),
                new Slf4jLoggerFactory());
    }

    @Nested
    @DisplayName("readSlot()")
    class ReadSlot {

        @Test
        @DisplayName("when a slot holds log behind it - then its retained bytes and its wal_status are recorded")
        void whenSlotHoldsLogBehindIt_thenRetainedBytesAndWalStatusAreRecorded() {
            when(replicationCatalogue.findSlotRetention(SLOT_NAME))
                    .thenReturn(Optional.of(new ReplicationSlotRetention(4096L, "extended")));

            replicationSlotMonitor.readSlot();

            assertThat(gauge(RETAINED_BYTES_METER).value()).isEqualTo(4096.0);
            assertThat(gauge(WAL_STATUS_METER).value()).isEqualTo(ReplicationSlotState.EXTENDED.ordinal());
        }

        @Test
        @DisplayName("when no slot of that name exists - then retained bytes is zero and wal_status is absent")
        void whenNoSlotOfThatNameExists_thenRetainedBytesIsZeroAndWalStatusIsAbsent() {
            when(replicationCatalogue.findSlotRetention(SLOT_NAME)).thenReturn(Optional.empty());

            replicationSlotMonitor.readSlot();

            assertThat(gauge(RETAINED_BYTES_METER).value()).isZero();
            assertThat(gauge(WAL_STATUS_METER).value()).isEqualTo(ReplicationSlotState.ABSENT.ordinal());
        }

        @Test
        @DisplayName("when Postgres has not classified the slot at all - then its wal_status is recorded as lost")
        void whenPostgresHasNotClassifiedTheSlotAtAll_thenWalStatusIsRecordedAsLost() {
            when(replicationCatalogue.findSlotRetention(SLOT_NAME))
                    .thenReturn(Optional.of(new ReplicationSlotRetention(0L, null)));

            replicationSlotMonitor.readSlot();

            assertThat(gauge(WAL_STATUS_METER).value()).isEqualTo(ReplicationSlotState.LOST.ordinal());
        }

        private Gauge gauge(String name) {
            return meterRegistry.find(name).gauge();
        }
    }
}
