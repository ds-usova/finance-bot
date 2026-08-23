package bot.finance.adapter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.MetricsAdapterTest;
import bot.finance.common.fixtures.PrometheusScrapes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * Drives the outbox meter port and asserts what a prometheus scrape actually renders - the counter's name, its
 * {@code type} tag and the count of facts it carries. This is where the rendered name and tags are proven since
 * {@code LedgerEventOutboxTest} verifies the port rather than the registry.
 */
@MetricsAdapterTest
class MicrometerOutboxMetersTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MicrometerOutboxMeters meters;

    @Nested
    @DisplayName("countFactsDropped()")
    class CountFactsDropped {

        @Test
        @DisplayName("when two dropped facts are counted under their type - then the scrape renders both under "
                + "the type tag")
        void whenTwoDroppedFactsCountedUnderTheirType_thenScrapeRendersBothUnderTypeTag() {
            meters.countFactsDropped("ProposalAccepted", 2);

            assertThat(scrapedValue("type=\"ProposalAccepted\"")).isEqualTo(2.0);
        }
    }

    private double scrapedValue(String tags) {
        return PrometheusScrapes.value(managementPort, MeterName.OUTBOX_FACTS_DROPPED.meterName(), tags);
    }
}
