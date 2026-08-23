package bot.finance.adapter.metrics;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.port.TurnMeters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class MicrometerTurnMeters implements TurnMeters {

    private final MeterRegistry registry;

    public MicrometerTurnMeters(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void countTurn(ReportOutcome outcome) {
        count(outcome.name());
    }

    @Override
    public void countUnreported() {
        count("UNREPORTED");
    }

    @Override
    public void countResolved(ProposalResolution resolution, int count) {
        Counter.builder(MeterName.PROPOSALS_RESOLVED.meterName())
                .tags(Tags.of("resolution", resolutionTag(resolution)))
                .register(registry)
                .increment(count);
    }

    private void count(String outcome) {
        Counter.builder(MeterName.TURNS.meterName())
                .tags(Tags.of("outcome", outcome))
                .register(registry)
                .increment();
    }

    private static String resolutionTag(ProposalResolution resolution) {
        return switch (resolution) {
            case ACCEPT -> "accepted";
            case DISCARD -> "discarded";
        };
    }
}
