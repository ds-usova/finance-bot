package bot.finance.ai.adapter.metrics;

import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.value.ExtractionMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class MicrometerTurnMeters implements TurnMeters {

    private final MeterRegistry meterRegistry;

    public MicrometerTurnMeters(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordTurn(ExtractionMode mode, boolean actedOn) {
        // stub-intent: counts the turn on ai_turns_total tagged with the mode's tag value and the outcome acted_on
        // or unavailable
    }

    @Override
    public void recordClassifierCall(Duration duration, boolean answered) {
        // stub-intent: records the duration on ai_classifier_seconds tagged with the outcome answered or failed
    }

    @Override
    public void recordConfidence(double confidence) {
        // stub-intent: records the confidence as one sample of ai_classifier_confidence
    }

    @Override
    public void countLowConfidence() {
        // stub-intent: increments ai_classifier_low_confidence_total
    }
}
