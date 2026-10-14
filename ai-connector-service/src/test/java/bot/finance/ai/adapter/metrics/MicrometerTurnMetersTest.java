package bot.finance.ai.adapter.metrics;

import static bot.finance.ai.common.PrometheusScrapeUtils.scrape;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.common.boot.MetricsAdapterTest;
import bot.finance.ai.domain.value.ExtractionMode;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * Drives {@link TurnMeters} and asserts the rendered Prometheus text on {@code GET /actuator/prometheus}. The
 * context is shared with the other scrape tests, so every assertion is a delta over the scrape taken before the
 * call; a sample the scrape does not carry yet reads as zero, since a tagged meter appears on its first use.
 */
@MetricsAdapterTest
class MicrometerTurnMetersTest {

    @Autowired
    private TurnMeters turnMeters;

    @LocalManagementPort
    private int managementPort;

    /**
     * The value of the sample named {@code metric} whose labels include every one of {@code labels}, each written
     * {@code name="value"}; zero when the scrape holds no such sample.
     */
    private static double sampleOrZero(String body, String metric, String... labels) {
        for (String line : body.split("\n")) {
            if (!line.startsWith(metric + "{") && !line.startsWith(metric + " ")) {
                continue;
            }
            boolean tagged = line.startsWith(metric + "{");
            if (!tagged && labels.length > 0) {
                continue;
            }
            if (tagged && labels.length == 0) {
                continue;
            }
            int labelsEnd = line.indexOf('}');
            String renderedLabels = tagged ? line.substring(0, labelsEnd) : "";
            if (Arrays.stream(labels).allMatch(renderedLabels::contains)) {
                return Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1));
            }
        }
        return 0.0;
    }

    @Nested
    @DisplayName("recordTurn()")
    class RecordTurn {

        @Test
        @DisplayName("when a classifier turn acted on and a tools turn unavailable are recorded - then each is "
                + "counted under its tags")
        void whenClassifierTurnActedOnAndToolsTurnUnavailable_thenEachIsCountedUnderItsTags() {
            String metric = MeterName.TURNS.meterName();
            String before = scrape(managementPort);
            double actedOnBefore = sampleOrZero(before, metric, "mode=\"classifier\"", "outcome=\"acted_on\"");
            double unavailableBefore = sampleOrZero(before, metric, "mode=\"tools\"", "outcome=\"unavailable\"");

            turnMeters.recordTurn(ExtractionMode.CLASSIFIER, true);
            turnMeters.recordTurn(ExtractionMode.TOOLS, false);

            String body = scrape(managementPort);
            assertThat(sampleOrZero(body, metric, "mode=\"classifier\"", "outcome=\"acted_on\""))
                    .isEqualTo(actedOnBefore + 1);
            assertThat(sampleOrZero(body, metric, "mode=\"tools\"", "outcome=\"unavailable\""))
                    .isEqualTo(unavailableBefore + 1);
        }
    }

    @Nested
    @DisplayName("recordClassifierCall()")
    class RecordClassifierCall {

        @Test
        @DisplayName("when an answered call and a failed call are recorded - then each outcome counts one")
        void whenAnsweredCallAndFailedCallAreRecorded_thenEachOutcomeCountsOne() {
            String metric = MeterName.CLASSIFIER_SECONDS.meterName() + "_count";
            String before = scrape(managementPort);
            double answeredBefore = sampleOrZero(before, metric, "outcome=\"answered\"");
            double failedBefore = sampleOrZero(before, metric, "outcome=\"failed\"");

            turnMeters.recordClassifierCall(Duration.ofMillis(120), true);
            turnMeters.recordClassifierCall(Duration.ofMillis(3000), false);

            String body = scrape(managementPort);
            assertThat(sampleOrZero(body, metric, "outcome=\"answered\"")).isEqualTo(answeredBefore + 1);
            assertThat(sampleOrZero(body, metric, "outcome=\"failed\"")).isEqualTo(failedBefore + 1);
        }
    }

    @Nested
    @DisplayName("recordConfidence()")
    class RecordConfidence {

        @Test
        @DisplayName("when a confidence of 0.8 is recorded - then the scrape's sample count grows by one")
        void whenConfidenceIsRecorded_thenScrapeSampleCountGrowsByOne() {
            String metric = MeterName.CLASSIFIER_CONFIDENCE.meterName() + "_count";
            double before = sampleOrZero(scrape(managementPort), metric);

            turnMeters.recordConfidence(0.8);

            assertThat(sampleOrZero(scrape(managementPort), metric)).isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("countLowConfidence()")
    class CountLowConfidence {

        @Test
        @DisplayName("when a low confidence is counted - then the scrape's low-confidence total grows by one")
        void whenLowConfidenceIsCounted_thenScrapeTotalGrowsByOne() {
            String metric = MeterName.CLASSIFIER_LOW_CONFIDENCE.meterName();
            double before = sampleOrZero(scrape(managementPort), metric);

            turnMeters.countLowConfidence();

            assertThat(sampleOrZero(scrape(managementPort), metric)).isEqualTo(before + 1);
        }
    }
}
