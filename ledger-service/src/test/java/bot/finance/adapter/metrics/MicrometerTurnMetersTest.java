package bot.finance.adapter.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.common.boot.MetricsAdapterTest;
import bot.finance.common.fixtures.PrometheusScrapes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * Drives the turn meter port and asserts what a prometheus scrape actually renders - the outcome names, the
 * {@code UNREPORTED} tag, and the {@code ACCEPT}/{@code DISCARD} to {@code accepted}/{@code discarded} mapping.
 * The resolved-counter tests read the counter's growth rather than its value, since the context and its
 * registry are shared across every {@link MetricsAdapterTest} class.
 */
@MetricsAdapterTest
class MicrometerTurnMetersTest {

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private MicrometerTurnMeters meters;

    @Nested
    @DisplayName("countTurn()")
    class CountTurn {

        @ParameterizedTest(name = "{0}")
        @EnumSource(ReportOutcome.class)
        @DisplayName("when a turn is counted under an outcome - then the scrape renders the counter under that "
                + "outcome's name")
        void whenTurnCountedUnderOutcome_thenScrapeRendersCounterUnderThatOutcomeName(ReportOutcome outcome) {
            meters.countTurn(outcome);

            assertThat(scrapedValue("ledger_turns_total", "outcome=\"" + outcome.name() + "\""))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("countUnreported()")
    class CountUnreported {

        @Test
        @DisplayName("when an unreported turn is counted - then the scrape renders the counter under UNREPORTED")
        void whenUnreportedTurnCounted_thenScrapeRendersCounterUnderUnreported() {
            meters.countUnreported();

            assertThat(scrapedValue("ledger_turns_total", "outcome=\"UNREPORTED\""))
                    .isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("countResolved()")
    class CountResolved {

        @Test
        @DisplayName("when two proposals are counted resolved as ACCEPT - then the accepted counter grows by two")
        void whenTwoProposalsCountedResolvedAsAccept_thenAcceptedCounterGrowsByTwo() {
            double before = scrapedValue("ledger_proposals_resolved_total", "resolution=\"accepted\"");

            meters.countResolved(ProposalResolution.ACCEPT, 2);

            double after = scrapedValue("ledger_proposals_resolved_total", "resolution=\"accepted\"");
            assertThat(after - before).isEqualTo(2.0);
        }

        @Test
        @DisplayName("when a proposal is counted resolved as DISCARD - then the scrape renders it under discarded")
        void whenProposalCountedResolvedAsDiscard_thenScrapeRendersItUnderDiscarded() {
            double before = scrapedValue("ledger_proposals_resolved_total", "resolution=\"discarded\"");

            meters.countResolved(ProposalResolution.DISCARD, 1);

            double after = scrapedValue("ledger_proposals_resolved_total", "resolution=\"discarded\"");
            assertThat(after - before).isEqualTo(1.0);
        }
    }

    private double scrapedValue(String meter, String tags) {
        return PrometheusScrapes.value(managementPort, meter, tags);
    }
}
