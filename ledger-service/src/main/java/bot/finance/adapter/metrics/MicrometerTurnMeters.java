package bot.finance.adapter.metrics;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnChannel;
import bot.finance.application.port.TurnMeters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MicrometerTurnMeters implements TurnMeters {

    private final MeterRegistry registry;
    private final Map<TurnChannel, AtomicLong> oldestQueuedMillis = new ConcurrentHashMap<>();

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

    @Override
    public void countSwept(TurnChannel channel) {
        Counter.builder(MeterName.TURNS_SWEPT.meterName())
                .tags(Tags.of("channel", channelTag(channel)))
                .register(registry)
                .increment();
    }

    @Override
    public void recordOldestQueued(TurnChannel channel, Duration age) {
        oldestQueuedMillis
                .computeIfAbsent(channel, this::registeredOldestQueued)
                .set(age.toMillis());
    }

    private AtomicLong registeredOldestQueued(TurnChannel channel) {
        AtomicLong millis = new AtomicLong();
        Gauge.builder(MeterName.TURN_QUEUE_OLDEST.meterName(), millis, value -> value.get() / 1000.0)
                .tags(Tags.of("channel", channelTag(channel)))
                .register(registry);
        return millis;
    }

    private static String channelTag(TurnChannel channel) {
        return switch (channel) {
            case INCOMING -> "telegram";
            case WRITTEN -> "web";
        };
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
