package bot.finance.adapter.metrics;

import bot.finance.application.port.OutboxMeters;
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
public class MicrometerOutboxMeters implements OutboxMeters {

    private final MeterRegistry registry;

    public MicrometerOutboxMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void countFactsDropped(String type, long facts) {
        Counter.builder("ledger_cdc_facts_dropped_total")
                .tags(Tags.of("type", type))
                .register(registry)
                .increment(facts);
    }
}
