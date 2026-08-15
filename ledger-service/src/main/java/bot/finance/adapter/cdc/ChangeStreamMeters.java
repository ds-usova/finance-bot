package bot.finance.adapter.cdc;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * The pipeline's eight meters, recorded by the reader, the publisher, the resolver and the slot monitor —
 * never read back inside the module, only scraped at {@code /actuator/prometheus}.
 */
@Component
public class ChangeStreamMeters {

    private final MeterRegistry registry;
    private final AtomicLong eventLagSeconds = new AtomicLong();
    private final AtomicLong state = new AtomicLong(ChangeStreamState.DOWN.ordinal());
    private final AtomicLong slotRetainedBytes = new AtomicLong();
    private final AtomicLong slotWalStatus = new AtomicLong();

    public ChangeStreamMeters(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("ledger_cdc_event_lag_seconds", eventLagSeconds, AtomicLong::get);
        registry.gauge("ledger_cdc_state", state, AtomicLong::get);
        registry.gauge("ledger_cdc_slot_retained_bytes", slotRetainedBytes, AtomicLong::get);
        registry.gauge("ledger_cdc_slot_wal_status", slotWalStatus, AtomicLong::get);
    }

    public void countPublished(String table, String operation) {
        counter("ledger_cdc_events_published_total", Tags.of("table", table, "op", operation))
                .increment();
    }

    public void countPublishFailure() {
        counter("ledger_cdc_publish_failures_total", Tags.empty()).increment();
    }

    public void countCategoryLookupHit() {
        counter("ledger_cdc_category_lookups_total", Tags.of("result", "hit")).increment();
    }

    public void countCategoryLookupMiss() {
        counter("ledger_cdc_category_lookups_total", Tags.of("result", "miss")).increment();
    }

    public void countCategoryLookupFailure() {
        counter("ledger_cdc_category_lookup_failures_total", Tags.empty()).increment();
    }

    public void setEventLag(Instant lastPublishedEventTimestamp) {
        eventLagSeconds.set(
                Duration.between(lastPublishedEventTimestamp, Instant.now()).getSeconds());
    }

    public void setState(ChangeStreamState changeStreamState) {
        state.set(changeStreamState.ordinal());
    }

    public void setSlotRetainedBytes(long retainedBytes) {
        slotRetainedBytes.set(retainedBytes);
    }

    public void setSlotWalStatus(ReplicationSlotState replicationSlotState) {
        slotWalStatus.set(replicationSlotState.ordinal());
    }

    private Counter counter(String name, Tags tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }
}
