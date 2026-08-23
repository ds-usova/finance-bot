package bot.finance.ai.adapter.metrics;

import static bot.finance.ai.common.PrometheusScrapeUtils.sample;
import static bot.finance.ai.common.PrometheusScrapeUtils.scrape;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.application.port.RecallMeters;
import bot.finance.ai.common.boot.MetricsAdapterTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * Drives {@link RecallMeters} and asserts the rendered Prometheus text on {@code GET /actuator/prometheus} —
 * the normalised {@code ai_recall_examples_*} and {@code ai_recall_best_similarity_*} count, sum and max lines.
 * The context is shared with the other scrape test, so every assertion is a delta over the scrape taken before
 * the call, never an absolute value.
 */
@MetricsAdapterTest
class MicrometerRecallMetersTest {

    @Autowired
    private RecallMeters recallMeters;

    @LocalManagementPort
    private int managementPort;

    @Nested
    @DisplayName("recordExamples()")
    class RecordExamples {

        @Test
        @DisplayName("when a returned count is recorded - then the scrape's count grows by one and its sum by "
                + "the count")
        void whenReturnedCountIsRecorded_thenScrapeCountGrowsByOneAndSumByCount() {
            String before = scrape(managementPort);
            double countBefore = sample(before, MeterName.RECALL_EXAMPLES.meterName() + "_count");
            double sumBefore = sample(before, MeterName.RECALL_EXAMPLES.meterName() + "_sum");

            recallMeters.recordExamples(3);

            String body = scrape(managementPort);
            assertThat(sample(body, MeterName.RECALL_EXAMPLES.meterName() + "_count"))
                    .isEqualTo(countBefore + 1);
            assertThat(sample(body, MeterName.RECALL_EXAMPLES.meterName() + "_sum"))
                    .isEqualTo(sumBefore + 3);
            assertThat(sample(body, MeterName.RECALL_EXAMPLES.meterName() + "_max"))
                    .isGreaterThanOrEqualTo(3);
        }
    }

    @Nested
    @DisplayName("recordBestSimilarity()")
    class RecordBestSimilarity {

        @Test
        @DisplayName("when a similarity score is recorded - then the scrape's count grows by one and its sum by "
                + "the score")
        void whenSimilarityScoreIsRecorded_thenScrapeCountGrowsByOneAndSumByScore() {
            String before = scrape(managementPort);
            double countBefore = sample(before, MeterName.RECALL_BEST_SIMILARITY.meterName() + "_count");
            double sumBefore = sample(before, MeterName.RECALL_BEST_SIMILARITY.meterName() + "_sum");

            recallMeters.recordBestSimilarity(0.75);

            String body = scrape(managementPort);
            assertThat(sample(body, MeterName.RECALL_BEST_SIMILARITY.meterName() + "_count"))
                    .isEqualTo(countBefore + 1);
            assertThat(sample(body, MeterName.RECALL_BEST_SIMILARITY.meterName() + "_sum"))
                    .isEqualTo(sumBefore + 0.75);
            assertThat(sample(body, MeterName.RECALL_BEST_SIMILARITY.meterName() + "_max"))
                    .isGreaterThanOrEqualTo(0.75);
        }
    }
}
