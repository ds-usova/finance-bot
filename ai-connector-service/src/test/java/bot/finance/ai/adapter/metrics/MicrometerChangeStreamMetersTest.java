package bot.finance.ai.adapter.metrics;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.port.ChangeStreamMeters;
import bot.finance.ai.application.port.PendingEntryCountPort;
import bot.finance.ai.common.boot.MetricsAdapterTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * Drives {@link ChangeStreamMeters} and asserts the rendered Prometheus text on
 * {@code GET /actuator/prometheus} — the {@code ai_cdc_deliveries_dropped_total} counter as a delta over the
 * scrape taken before the call (the context is shared with the other scrape test), and the
 * {@code ai_cdc_entries_pending} gauge sampled from the mocked {@link PendingEntryCountPort} at scrape time.
 */
@MetricsAdapterTest
class MicrometerChangeStreamMetersTest {

    @Autowired
    private ChangeStreamMeters changeStreamMeters;

    @Autowired
    private PendingEntryCountPort pendingEntryCount;

    @LocalManagementPort
    private int managementPort;

    private String scrape() {
        return given().port(managementPort)
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    private static double sample(String body, String metricName) {
        Matcher matcher = Pattern.compile("(?m)^" + Pattern.quote(metricName) + "\\s+(\\S+)$")
                .matcher(body);
        assertThat(matcher.find()).as(metricName + " sample present").isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    @Nested
    @DisplayName("countDropped()")
    class CountDropped {

        @Test
        @DisplayName("when a delivery is dropped - then the scrape's dropped total grows by one")
        void whenDeliveryIsDropped_thenScrapeDroppedTotalGrowsByOne() {
            double before = sample(scrape(), "ai_cdc_deliveries_dropped_total");

            changeStreamMeters.countDropped();

            assertThat(sample(scrape(), "ai_cdc_deliveries_dropped_total")).isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("the pending gauge")
    class PendingGauge {

        @Test
        @DisplayName("when the pending count answers a value - then the scrape's gauge renders it")
        void whenPendingCountAnswersValue_thenScrapeGaugeRendersIt() {
            when(pendingEntryCount.count()).thenReturn(4.0);

            assertThat(sample(scrape(), "ai_cdc_entries_pending")).isEqualTo(4.0);
        }

        @Test
        @DisplayName("when the pending count answers NaN - then the scrape's gauge renders a NaN sample")
        void whenPendingCountAnswersNaN_thenScrapeGaugeRendersNaNSample() {
            when(pendingEntryCount.count()).thenReturn(Double.NaN);

            assertThat(sample(scrape(), "ai_cdc_entries_pending")).isNaN();
        }
    }
}
