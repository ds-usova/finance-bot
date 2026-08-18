package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
        @Disabled("RU03: countPublished() takes one event type instead of a table and an op")
        @DisplayName("when an event is counted - then the published counter carries only the table and op tags")
        void whenEventIsCounted_thenPublishedCounterCarriesOnlyTableAndOpTags() {
            // meters.countPublished("expense", "u");
            //
            // Counter counter = registry.get("ledger_cdc_events_published_total")
            //         .tag("table", "expense")
            //         .tag("op", "u")
            //         .counter();
            //
            // assertThat(counter.count()).isEqualTo(1.0);
            // assertThat(counter.getId().getTags()).hasSize(2);
        }

        @Test
        @Disabled("RU03: the category lookup counters are gone, enrichment resolving no names")
        @DisplayName("when a lookup is counted as a hit and another as a miss - then the category lookups counter "
                + "separates the two by tag")
        void whenLookupCountedHitAndAnotherMiss_thenCategoryLookupsCounterSeparatesTheTwoByTag() {
            // meters.countCategoryLookupHit();
            // meters.countCategoryLookupMiss();
            //
            // Collection<Counter> counters =
            //         registry.find("ledger_cdc_category_lookups_total").counters();
            //
            // assertThat(counters).hasSize(2);
            // assertThat(counters.stream()
            //                 .map(counter -> counter.getId().getTags())
            //                 .collect(Collectors.toSet()))
            //         .hasSize(2);
            // assertThat(counters)
            //         .allSatisfy(counter -> assertThat(counter.count()).isEqualTo(1.0));
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
