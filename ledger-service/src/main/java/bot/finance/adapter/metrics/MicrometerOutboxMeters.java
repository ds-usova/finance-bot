package bot.finance.adapter.metrics;

import bot.finance.application.port.OutboxMeters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class MicrometerOutboxMeters implements OutboxMeters {

    private final MeterRegistry registry;

    public MicrometerOutboxMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void countFactsDropped(String type, long facts) {
        Counter.builder(MeterName.OUTBOX_FACTS_DROPPED.meterName())
                .tags(Tags.of("type", type))
                .register(registry)
                .increment(facts);
    }
}
