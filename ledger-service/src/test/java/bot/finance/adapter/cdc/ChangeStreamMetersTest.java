package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChangeStreamMetersTest {

    private SimpleMeterRegistry registry;
    private ChangeStreamMeters meters;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        meters = new ChangeStreamMeters(registry);
    }

    @Nested
    @DisplayName("recording the counters and the gauges against a real registry")
    class TheCountersAndTheGauges {

        @Test
        @DisplayName("when an event is counted - then the published counter carries one type tag")
        void whenEventIsCounted_thenPublishedCounterCarriesOneTypeTag() {
            meters.countPublished("ProposalAccepted");

            Counter counter = registry.get("ledger_cdc_events_published_total")
                    .tag("type", "ProposalAccepted")
                    .counter();

            assertThat(counter.count()).isEqualTo(1.0);
            assertThat(counter.getId().getTags()).hasSize(1);
        }

        @Test
        @DisplayName("when the outbox row count is set to a non-zero value - then the outbox rows gauge reads "
                + "that value back")
        void whenOutboxRowCountIsSetToNonZeroValue_thenOutboxRowsGaugeReadsThatValueBack() {
            meters.setOutboxRows(3L);

            assertThat(registry.get("ledger_cdc_outbox_rows").gauge().value()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("when each gauge is set in turn - then the registry reads back the value each was set to")
        void whenEachGaugeIsSetInTurn_thenRegistryReadsBackTheValueEachWasSetTo() {
            Instant lastPublished = Instant.now().minusSeconds(120);

            meters.setEventLag(lastPublished);
            meters.setState(ChangeStreamState.STANDBY);
            meters.setSlotRetainedBytes(65_536L);
            meters.setSlotWalStatus(ReplicationSlotState.EXTENDED);

            assertThat(registry.get("ledger_cdc_event_lag_seconds").gauge().value())
                    .isCloseTo(120.0, within(5.0));
            assertThat(registry.get("ledger_cdc_state").gauge().value()).isEqualTo(ChangeStreamState.STANDBY.ordinal());
            assertThat(registry.get("ledger_cdc_slot_retained_bytes").gauge().value())
                    .isEqualTo(65_536.0);
            assertThat(registry.get("ledger_cdc_slot_wal_status").gauge().value())
                    .isEqualTo(ReplicationSlotState.EXTENDED.ordinal());
        }
    }
}
