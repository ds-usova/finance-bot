package bot.finance.adapter.metrics;

import bot.finance.application.port.ToolCallMeters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class MicrometerToolCallMeters implements ToolCallMeters {

    private final MeterRegistry registry;

    public MicrometerToolCallMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void countOk(String tool) {
        count(tool, "ok", "none");
    }

    @Override
    public void countRejected(String tool, String reason) {
        count(tool, "rejected", reason);
    }

    private void count(String tool, String outcome, String reason) {
        Counter.builder(MeterName.TOOL_CALLS.meterName())
                .tags(Tags.of("tool", tool, "outcome", outcome, "reason", reason))
                .register(registry)
                .increment();
    }
}
