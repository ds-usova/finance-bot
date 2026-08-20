package bot.finance.ai.adapter.metrics;

import bot.finance.ai.application.port.ChangeStreamMeters;
import bot.finance.ai.application.port.PendingEntryCountPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class MicrometerChangeStreamMeters implements ChangeStreamMeters {

    private final Counter dropped;

    public MicrometerChangeStreamMeters(MeterRegistry meterRegistry, PendingEntryCountPort pendingEntryCount) {
        this.dropped = Counter.builder("ai_cdc_deliveries_dropped_total").register(meterRegistry);
        // Sampled at each scrape via the port itself, never a locally constructed holder: Micrometer holds a
        // gauge's state object weakly and a holder with no other reference would decay to NaN after a GC.
        Gauge.builder("ai_cdc_entries_pending", pendingEntryCount, PendingEntryCountPort::count)
                .register(meterRegistry);
    }

    @Override
    public void countDropped() {
        dropped.increment();
    }
}
