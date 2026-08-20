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
 * Drives the tool-call meter port and asserts what a prometheus scrape actually renders - the normalised name
 * and the three tags a dashboard queries by. Each test counts under its own tool tag, since the context and its
 * registry are shared across every {@link MetricsAdapterTest} class.
 */
@MetricsAdapterTest
class MicrometerToolCallMetersTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MicrometerToolCallMeters meters;

    @Nested
    @DisplayName("countOk()")
    class CountOk {

        @Test
        @DisplayName("when a tool call is counted ok - then the scrape renders the counter under outcome ok and "
                + "reason none")
        void whenToolCallCountedOk_thenScrapeRendersCounterUnderOutcomeOkAndReasonNone() {
            meters.countOk("list_categories");

            assertThat(scrapedValue("outcome=\"ok\",reason=\"none\",tool=\"list_categories\""))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("countRejected()")
    class CountRejected {

        @Test
        @DisplayName("when a rejection carries an exception's simple name - then the scrape renders it as the "
                + "reason tag")
        void whenRejectionCarriesExceptionSimpleName_thenScrapeRendersItAsReasonTag() {
            meters.countRejected("create_expense_proposal", "InvalidExpenseException");

            assertThat(scrapedValue(
                            "outcome=\"rejected\",reason=\"InvalidExpenseException\",tool=\"create_expense_proposal\""))
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("when a rejection carries the unexpected reason - then the scrape renders it under outcome "
                + "rejected")
        void whenRejectionCarriesUnexpectedReason_thenScrapeRendersItUnderOutcomeRejected() {
            meters.countRejected("summarize_spending", "unexpected");

            assertThat(scrapedValue("outcome=\"rejected\",reason=\"unexpected\",tool=\"summarize_spending\""))
                    .isEqualTo(1.0);
        }
    }

    private double scrapedValue(String tags) {
        return PrometheusScrapes.value(managementPort, "ledger_mcp_tool_calls_total", tags);
    }
}
