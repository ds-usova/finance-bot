package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProposalReportUtilsTest {

    private static final CurrencyCode EUR = CurrencyCode.of("EUR");

    @Nested
    @DisplayName("rendering a proposal report into message text")
    class Render {

        @Test
        @DisplayName(
                "when a RECORDED report carries two summaries, one with a merchant and one without - then opens with the plural count and lists one bullet per summary in order")
        void whenRecordedReportCarriesTwoSummariesOneWithMerchantOneWithout_thenOpensWithPluralCountAndListsOneBulletPerSummaryInOrder() {
            ProposalSummary withMerchant = new ProposalSummary(
                    "Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            ProposalSummary withoutMerchant =
                    new ProposalSummary("Auto", "Fuel", "tank refill", Optional.empty(), new Money(6000, EUR));
            ProposalReport report =
                    new ProposalReport("555", "1", ReportOutcome.RECORDED, List.of(withMerchant, withoutMerchant));

            String text = ProposalReportUtils.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Noted 2 expenses, pending your confirmation:
                            • Groceries (Food) — weekly shop, Rewe: 42.30 EUR
                            • Auto (Fuel) — tank refill: 60.00 EUR""");
        }

        @Test
        @DisplayName("when a RECORDED report carries exactly one summary - then opens with the singular count")
        void whenRecordedReportCarriesExactlyOneSummary_thenOpensWithSingularCount() {
            ProposalSummary summary = new ProposalSummary(
                    "Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            ProposalReport report = new ProposalReport("555", "1", ReportOutcome.RECORDED, List.of(summary));

            String text = ProposalReportUtils.render(report);

            assertThat(text).startsWith("Noted 1 expense, pending your confirmation:");
        }

        @Test
        @DisplayName("when a NOTHING_IDENTIFIED report has no summaries - then renders the no-expense-identified text")
        void whenNothingIdentifiedReportHasNoSummaries_thenRendersNoExpenseIdentifiedText() {
            ProposalReport report = new ProposalReport("555", "1", ReportOutcome.NOTHING_IDENTIFIED, List.of());

            String text = ProposalReportUtils.render(report);

            assertThat(text).isEqualTo("No expense was identified in that message.");
        }

        @Test
        @DisplayName("when a FAILED report has no summaries - then renders the went-wrong text")
        void whenFailedReportHasNoSummaries_thenRendersWentWrongText() {
            ProposalReport report = new ProposalReport("555", "1", ReportOutcome.FAILED, List.of());

            String text = ProposalReportUtils.render(report);

            assertThat(text).isEqualTo("Something went wrong and nothing was noted — please try again.");
        }

        @Test
        @DisplayName(
                "when a PARTIAL report carries one summary - then opens with the may-be-incomplete text and carries that summary's bullet below it")
        void whenPartialReportCarriesOneSummary_thenOpensWithMayBeIncompleteTextAndCarriesBulletBelowIt() {
            ProposalSummary summary = new ProposalSummary(
                    "Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            ProposalReport report = new ProposalReport("555", "1", ReportOutcome.PARTIAL, List.of(summary));

            String text = ProposalReportUtils.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Something went wrong, so this may be incomplete. What I could read:
                            • Groceries (Food) — weekly shop, Rewe: 42.30 EUR""");
        }

        @Test
        @DisplayName(
                "when a RECORDED report carries enough summaries that the bullets would exceed 4000 characters - then the text is cut at 4000 characters and ends with the omitted-count line")
        void whenRecordedReportExceeds4000Characters_thenTextIsCutAt4000CharactersAndEndsWithOmittedCountLine() {
            int summaryCount = 200;
            List<ProposalSummary> summaries = IntStream.range(0, summaryCount)
                    .mapToObj(i -> new ProposalSummary(
                            "Groceries",
                            "Food",
                            "a fairly long weekly shopping description number " + i,
                            Optional.of("Rewe supermarket"),
                            new Money(4230, EUR)))
                    .toList();
            ProposalReport report = new ProposalReport("555", "1", ReportOutcome.RECORDED, summaries);

            String text = ProposalReportUtils.render(report);

            assertThat(text.length()).isLessThanOrEqualTo(4000);

            Pattern omittedPattern = Pattern.compile("… and (\\d+) more\\.$");
            Matcher matcher = omittedPattern.matcher(text);
            assertThat(matcher.find()).isTrue();
            int omittedCount = Integer.parseInt(matcher.group(1));

            int bulletCount = text.split("\n", -1).length
                    - 1 // header line
                    - 1; // omitted-count line
            assertThat(bulletCount + omittedCount).isEqualTo(summaryCount);
            assertThat(omittedCount).isGreaterThan(0);
        }

        @Test
        @DisplayName(
                "when a summary's description contains * and _ - then those characters appear literally in the rendered bullet, with no escaping applied")
        void whenSummaryDescriptionContainsAsteriskAndUnderscore_thenThoseCharactersAppearLiterallyWithNoEscaping() {
            ProposalSummary summary = new ProposalSummary(
                    "Groceries", "Food", "weekly *shop_ trip", Optional.empty(), new Money(4230, EUR));
            ProposalReport report = new ProposalReport("555", "1", ReportOutcome.RECORDED, List.of(summary));

            String text = ProposalReportUtils.render(report);

            assertThat(text).contains("weekly *shop_ trip");
            assertThat(text).doesNotContain("\\*").doesNotContain("\\_");
        }
    }
}
