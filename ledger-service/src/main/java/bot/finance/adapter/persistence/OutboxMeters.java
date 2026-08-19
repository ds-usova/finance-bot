package bot.finance.adapter.persistence;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/**
 * What the outbox could not record. A dropped fact reaches no consumer and leaves the ledger and whatever reads
 * its stream permanently apart, so it is counted rather than only logged — nothing else in the pipeline can see
 * a write that never became a row.
 */
@Component
public class OutboxMeters {

    private final MeterRegistry registry;

    public OutboxMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countFactsDropped(LedgerEventType type, long facts) {
        Counter.builder("ledger_cdc_facts_dropped_total")
                .tags(Tags.of("type", type.name()))
                .register(registry)
                .increment(facts);
    }
}
