package bot.finance.ai.adapter.metrics;

import static bot.finance.ai.common.PrometheusScrapeUtils.sample;
import static bot.finance.ai.common.PrometheusScrapeUtils.scrape;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.port.ChangeStreamMeters;
import bot.finance.ai.application.port.PendingEntryCountPort;
import bot.finance.ai.common.boot.MetricsAdapterTest;
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

    @Nested
    @DisplayName("countDropped()")
    class CountDropped {

        @Test
        @DisplayName("when a delivery is dropped - then the scrape's dropped total grows by one")
        void whenDeliveryIsDropped_thenScrapeDroppedTotalGrowsByOne() {
            double before = sample(scrape(managementPort), MeterName.CDC_DELIVERIES_DROPPED.meterName());

            changeStreamMeters.countDropped();

            assertThat(sample(scrape(managementPort), MeterName.CDC_DELIVERIES_DROPPED.meterName()))
                    .isEqualTo(before + 1);
        }
    }

    @Nested
    @DisplayName("the pending gauge")
    class PendingGauge {

        @Test
        @DisplayName("when the pending count answers a value - then the scrape's gauge renders it")
        void whenPendingCountAnswersValue_thenScrapeGaugeRendersIt() {
            when(pendingEntryCount.count()).thenReturn(4.0);

            assertThat(sample(scrape(managementPort), MeterName.CDC_ENTRIES_PENDING.meterName()))
                    .isEqualTo(4.0);
        }

        @Test
        @DisplayName("when the pending count answers NaN - then the scrape's gauge renders a NaN sample")
        void whenPendingCountAnswersNaN_thenScrapeGaugeRendersNaNSample() {
            when(pendingEntryCount.count()).thenReturn(Double.NaN);

            assertThat(sample(scrape(managementPort), MeterName.CDC_ENTRIES_PENDING.meterName()))
                    .isNaN();
        }
    }
}
