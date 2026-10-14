package bot.finance.ai.adapter.metrics;

import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.value.ExtractionMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MicrometerTurnMeters implements TurnMeters {

    private final MeterRegistry meterRegistry;
    private final DistributionSummary confidence;
    private final Counter lowConfidence;

    public MicrometerTurnMeters(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.confidence = DistributionSummary.builder(MeterName.CLASSIFIER_CONFIDENCE.meterName())
                .register(meterRegistry);
        this.lowConfidence =
                Counter.builder(MeterName.CLASSIFIER_LOW_CONFIDENCE.meterName()).register(meterRegistry);
    }

    @Override
    public void recordTurn(ExtractionMode mode, boolean actedOn) {
        Counter.builder(MeterName.TURNS.meterName())
                .tag("mode", mode.tagValue())
                .tag("outcome", actedOn ? "acted_on" : "unavailable")
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void recordClassifierCall(Duration duration, boolean answered) {
        Timer.builder(MeterName.CLASSIFIER_SECONDS.meterName())
                .tag("outcome", answered ? "answered" : "failed")
                .register(meterRegistry)
                .record(duration);
    }

    @Override
    public void recordConfidence(double confidence) {
        this.confidence.record(confidence);
    }

    @Override
    public void countLowConfidence() {
        lowConfidence.increment();
    }

    @Override
    public void countCategoryLookup(boolean answered) {
        // stub-intent: increments ai_category_lookups_total tagged outcome=answered or outcome=refused
    }
}
