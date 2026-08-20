package bot.finance.ai.adapter.metrics;

import bot.finance.ai.application.port.RecallMeters;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class MicrometerRecallMeters implements RecallMeters {

    private final DistributionSummary examples;
    private final DistributionSummary bestSimilarity;

    public MicrometerRecallMeters(MeterRegistry meterRegistry) {
        this.examples = DistributionSummary.builder("ai_recall_examples").register(meterRegistry);
        this.bestSimilarity =
                DistributionSummary.builder("ai_recall_best_similarity").register(meterRegistry);
    }

    @Override
    public void recordExamples(int returned) {
        examples.record(returned);
    }

    @Override
    public void recordBestSimilarity(double score) {
        bestSimilarity.record(score);
    }
}
