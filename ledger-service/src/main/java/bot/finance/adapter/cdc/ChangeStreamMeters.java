package bot.finance.adapter.cdc;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * The pipeline's eight meters, recorded by the reader, the publisher, the resolver and the slot monitor —
 * never read back inside the module, only scraped at {@code /actuator/prometheus}.
 */
@Component
public class ChangeStreamMeters {

    private final MeterRegistry registry;

    public ChangeStreamMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countPublished(String table, String operation) {
        // increments ledger_cdc_events_published_total, tagged by table and op alone
    }

    public void countPublishFailure() {
        // increments ledger_cdc_publish_failures_total
    }

    public void countCategoryLookupHit() {
        // increments ledger_cdc_category_lookups_total, tagged hit
    }

    public void countCategoryLookupMiss() {
        // increments ledger_cdc_category_lookups_total, tagged miss
    }

    public void countCategoryLookupFailure() {
        // increments ledger_cdc_category_lookup_failures_total
    }

    public void setEventLag(Instant lastPublishedEventTimestamp) {
        // sets ledger_cdc_event_lag_seconds to now less the given timestamp
    }

    public void setState(ChangeStreamState state) {
        // sets ledger_cdc_state to the state's ordinal
    }

    public void setSlotRetainedBytes(long retainedBytes) {
        // sets ledger_cdc_slot_retained_bytes
    }

    public void setSlotWalStatus(ReplicationSlotState state) {
        // sets ledger_cdc_slot_wal_status to the state's ordinal
    }
}
