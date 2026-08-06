package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnReport;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TurnReportRendererTest {

    private static final CurrencyCode EUR = CurrencyCode.of("EUR");

    @Nested
    @DisplayName("rendering a proposal report into message text")
    class Render {

        @Test
        @DisplayName(
                "when a RECORDED report carries two summaries, one with a merchant and one without - then opens with the plural count and lists one bullet per summary in order")
        void
                whenRecordedReportCarriesTwoSummariesOneWithMerchantOneWithout_thenOpensWithPluralCountAndListsOneBulletPerSummaryInOrder() {
            ProposalSummary withMerchant =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            ProposalSummary withoutMerchant =
                    new ProposalSummary("Auto", "Fuel", "tank refill", Optional.empty(), new Money(6000, EUR));
            TurnReport report = new TurnReport(
                    "555",
                    "1",
                    ReportOutcome.RECORDED,
                    List.of(withMerchant, withoutMerchant),
                    List.of(),
                    MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

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
            ProposalSummary summary =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.RECORDED, List.of(summary), List.of(), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text).startsWith("Noted 1 expense, pending your confirmation:");
        }

        @Test
        @DisplayName("when a NOTHING_IDENTIFIED report has no summaries - then renders the no-expense-identified text")
        void whenNothingIdentifiedReportHasNoSummaries_thenRendersNoExpenseIdentifiedText() {
            TurnReport report = new TurnReport(
                    "555",
                    "1",
                    ReportOutcome.NOTHING_IDENTIFIED,
                    List.of(),
                    List.of(),
                    MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text).isEqualTo("No expense was identified in that message.");
        }

        @Test
        @DisplayName("when a FAILED report has no summaries - then renders the went-wrong text")
        void whenFailedReportHasNoSummaries_thenRendersWentWrongText() {
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.FAILED, List.of(), List.of(), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text).isEqualTo("Something went wrong and nothing was noted — please try again.");
        }

        @Test
        @DisplayName(
                "when a PARTIAL report carries one summary - then opens with the may-be-incomplete text and carries that summary's bullet below it")
        void whenPartialReportCarriesOneSummary_thenOpensWithMayBeIncompleteTextAndCarriesBulletBelowIt() {
            ProposalSummary summary =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.PARTIAL, List.of(summary), List.of(), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

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
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.RECORDED, summaries, List.of(), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

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
                "when a RECORDED report's bullets would exceed 4000 characters and its last bullet is shorter than "
                        + "the omitted-count line - then the text is still cut at 4000 characters")
        void
                whenLastBulletIsShorterThanOmittedCountLineAndBulletsExceed4000Characters_thenTextIsStillCutAt4000Characters() {
            List<ProposalSummary> summaries = new ArrayList<>(IntStream.range(0, 48)
                    .mapToObj(i -> new ProposalSummary(
                            "Groceries", "Food", "d".repeat(31), Optional.of("Rewe supermarket"), new Money(4230, EUR)))
                    .toList());
            summaries.add(new ProposalSummary("A", "B", "c", Optional.empty(), new Money(100, EUR)));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.RECORDED, summaries, List.of(), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text.length()).isLessThanOrEqualTo(4000);
        }

        @Test
        @DisplayName(
                "when a summary's description contains * and _ - then those characters appear literally in the rendered bullet, with no escaping applied")
        void whenSummaryDescriptionContainsAsteriskAndUnderscore_thenThoseCharactersAppearLiterallyWithNoEscaping() {
            ProposalSummary summary = new ProposalSummary(
                    "Groceries", "Food", "weekly *shop_ trip", Optional.empty(), new Money(4230, EUR));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.RECORDED, List.of(summary), List.of(), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text).contains("weekly *shop_ trip");
            assertThat(text).doesNotContain("\\*").doesNotContain("\\_");
        }

        @Test
        @DisplayName(
                "when an ANSWERED report carries one summary with two currency totals, one of them a single expense - then the text is exactly the design's summary block")
        void whenAnsweredReportCarriesOneSummaryWithTwoCurrencyTotals_thenTextIsExactlyTheDesignsSummaryBlock() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            CurrencyTotal eurTotal = new CurrencyTotal(new Money(12050, EUR), 4);
            CurrencyTotal hufTotal = new CurrencyTotal(new Money(720000, CurrencyCode.of("HUF")), 1);
            SpendingSummary summary = new SpendingSummary(period, List.of(eurTotal, hufTotal));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.ANSWERED, List.of(), List.of(summary), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 120.50 EUR (4 expenses)
                            • 7200.00 HUF (1 expense)""");
        }

        @Test
        @DisplayName(
                "when an ANSWERED report carries a summary with no totals - then the text reports nothing is recorded between the period's dates")
        void whenAnsweredReportCarriesSummaryWithNoTotals_thenTextReportsNothingIsRecordedBetweenTheDates() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of());
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.ANSWERED, List.of(), List.of(summary), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text).isEqualTo("Nothing is recorded between 27 Jul 2026 and 2 Aug 2026.");
        }

        @Test
        @DisplayName(
                "when a summary's period runs from 27 Jul 2026 to 2 Aug 2026 and the JVM's default locale is non-English - then the dates read in English regardless")
        void whenSummaryPeriodIsRenderedWithNonEnglishDefaultLocale_thenDatesReadInEnglish() {
            Locale previousDefault = Locale.getDefault();
            try {
                Locale.setDefault(Locale.GERMANY);
                SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
                SpendingSummary summary = new SpendingSummary(period, List.of());
                TurnReport report = new TurnReport(
                        "555",
                        "1",
                        ReportOutcome.ANSWERED,
                        List.of(),
                        List.of(summary),
                        MessageReference.newReference());

                String text = TurnReportRenderer.render(report);

                assertThat(text).contains("27 Jul 2026").contains("2 Aug 2026");
            } finally {
                Locale.setDefault(previousDefault);
            }
        }

        @Test
        @DisplayName(
                "when an ANSWERED report carries two summaries - then both blocks appear, in the report's own order, separated so a reader can tell them apart")
        void whenAnsweredReportCarriesTwoSummaries_thenBothBlocksAppearInOrderSeparated() {
            SpendingPeriod firstPeriod = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary firstSummary =
                    new SpendingSummary(firstPeriod, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            SpendingPeriod secondPeriod = new SpendingPeriod(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9));
            SpendingSummary secondSummary = new SpendingSummary(secondPeriod, List.of());
            TurnReport report = new TurnReport(
                    "555",
                    "1",
                    ReportOutcome.ANSWERED,
                    List.of(),
                    List.of(firstSummary, secondSummary),
                    MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 42.30 EUR (1 expense)

                            Nothing is recorded between 3 Aug 2026 and 9 Aug 2026.""");
        }

        @Test
        @DisplayName(
                "when a RECORDED report carries one summary and two proposals - then the summary block comes first and the recorded-proposal header and its bullets follow it")
        void whenRecordedReportCarriesSummaryAndTwoProposals_thenSummaryBlockPrecedesRecordedHeaderAndBullets() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            ProposalSummary withMerchant =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            ProposalSummary withoutMerchant =
                    new ProposalSummary("Auto", "Fuel", "tank refill", Optional.empty(), new Money(6000, EUR));
            TurnReport report = new TurnReport(
                    "555",
                    "1",
                    ReportOutcome.RECORDED,
                    List.of(withMerchant, withoutMerchant),
                    List.of(summary),
                    MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 42.30 EUR (1 expense)

                            Noted 2 expenses, pending your confirmation:
                            • Groceries (Food) — weekly shop, Rewe: 42.30 EUR
                            • Auto (Fuel) — tank refill: 60.00 EUR""");
        }

        @Test
        @DisplayName(
                "when a PARTIAL report carries one summary and one proposal - then the summary block comes first, then the may-be-incomplete line and the proposal bullet")
        void whenPartialReportCarriesSummaryAndOneProposal_thenSummaryBlockPrecedesMayBeIncompleteLineAndBullet() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            ProposalSummary proposal =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            TurnReport report = new TurnReport(
                    "555",
                    "1",
                    ReportOutcome.PARTIAL,
                    List.of(proposal),
                    List.of(summary),
                    MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 42.30 EUR (1 expense)

                            Something went wrong, so this may be incomplete. What I could read:
                            • Groceries (Food) — weekly shop, Rewe: 42.30 EUR""");
        }

        @Test
        @DisplayName(
                "when a NOTHING_IDENTIFIED report carries one summary - then the summary block comes first and the no-expense-identified text follows it")
        void whenNothingIdentifiedReportCarriesSummary_thenSummaryBlockPrecedesNoExpenseIdentifiedText() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            TurnReport report = new TurnReport(
                    "555",
                    "1",
                    ReportOutcome.NOTHING_IDENTIFIED,
                    List.of(),
                    List.of(summary),
                    MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 42.30 EUR (1 expense)

                            No expense was identified in that message.""");
        }

        @Test
        @DisplayName(
                "when a FAILED report carries one summary - then the summary block comes first and the went-wrong text follows it")
        void whenFailedReportCarriesSummary_thenSummaryBlockPrecedesWentWrongText() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.FAILED, List.of(), List.of(summary), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text)
                    .isEqualTo(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 42.30 EUR (1 expense)

                            Something went wrong and nothing was noted — please try again.""");
        }

        @Test
        @DisplayName(
                "when a RECORDED report's summaries and proposals together exceed 4000 characters - then every summary line is intact and the proposal list is what trims, ending with its omitted-count line")
        void whenRecordedReportSummariesAndProposalsExceed4000Characters_thenSummaryLinesSurviveAndProposalListTrims() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            int proposalCount = 200;
            List<ProposalSummary> proposals = IntStream.range(0, proposalCount)
                    .mapToObj(i -> new ProposalSummary(
                            "Groceries",
                            "Food",
                            "a fairly long weekly shopping description number " + i,
                            Optional.of("Rewe supermarket"),
                            new Money(4230, EUR)))
                    .toList();
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.RECORDED, proposals, List.of(summary), MessageReference.newReference());

            String text = TurnReportRenderer.render(report);

            assertThat(text.length()).isLessThanOrEqualTo(4000);
            assertThat(text)
                    .startsWith(
                            """
                            Between 27 Jul 2026 and 2 Aug 2026 you spent:
                            • 42.30 EUR (1 expense)

                            Noted 200 expenses, pending your confirmation:""");

            Pattern omittedPattern = Pattern.compile("… and (\\d+) more\\.$");
            Matcher matcher = omittedPattern.matcher(text);
            assertThat(matcher.find()).isTrue();
            int omittedCount = Integer.parseInt(matcher.group(1));

            long bulletCount = text.lines()
                    .filter(line -> line.startsWith("• Groceries (Food)"))
                    .count();
            assertThat(bulletCount + omittedCount).isEqualTo(proposalCount);
            assertThat(omittedCount).isGreaterThan(0);
        }

        @Test
        @DisplayName(
                "when an ANSWERED report's summary blocks alone exceed 4000 characters - then whole periods are dropped from the oldest and the text ends with the omitted-periods line")
        void
                whenAnsweredReportSummaryBlocksAloneExceed4000Characters_thenOldestPeriodsDropAndTextEndsWithOmittedPeriodsLine() {
            int summaryCount = 150;
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
            List<SpendingSummary> summaries = IntStream.range(0, summaryCount)
                    .mapToObj(i -> {
                        LocalDate day = LocalDate.of(2026, 1, 1).plusDays(i);
                        return new SpendingSummary(new SpendingPeriod(day, day), List.of());
                    })
                    .toList();
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.ANSWERED, List.of(), summaries, MessageReference.newReference());
            String oldestBlock = "Nothing is recorded between %s and %s."
                    .formatted(
                            LocalDate.of(2026, 1, 1).format(dateFormat),
                            LocalDate.of(2026, 1, 1).format(dateFormat));
            String newestDay =
                    LocalDate.of(2026, 1, 1).plusDays(summaryCount - 1).format(dateFormat);
            String newestBlock = "Nothing is recorded between %s and %s.".formatted(newestDay, newestDay);

            String text = TurnReportRenderer.render(report);

            assertThat(text.length()).isLessThanOrEqualTo(4000);
            assertThat(text).doesNotContain(oldestBlock);
            assertThat(text).contains(newestBlock);

            Pattern omittedPattern = Pattern.compile("… and (\\d+) more periods\\.$");
            Matcher matcher = omittedPattern.matcher(text);
            assertThat(matcher.find()).isTrue();
            int omittedCount = Integer.parseInt(matcher.group(1));

            long blocksPresent = text.lines()
                    .filter(line -> line.startsWith("Nothing is recorded between"))
                    .count();
            assertThat(blocksPresent + omittedCount).isEqualTo(summaryCount);
            assertThat(omittedCount).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("rendering a proposal report's Confirm/Delete keyboard")
    class RenderKeyboard {

        @Test
        @DisplayName("when a RECORDED report carries summaries - then returns one row of a Confirm and a Delete button")
        void whenRecordedReportCarriesTwoSummariesAndReference_thenReturnsOneRowOfConfirmAndDeleteButtons() {
            MessageReference reference = MessageReference.newReference();
            ProposalSummary first =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            ProposalSummary second =
                    new ProposalSummary("Auto", "Fuel", "tank refill", Optional.empty(), new Money(6000, EUR));
            TurnReport report =
                    new TurnReport("555", "1", ReportOutcome.RECORDED, List.of(first, second), List.of(), reference);

            Optional<InlineKeyboardMarkup> markup = TurnReportRenderer.renderKeyboard(report);

            assertThat(markup).isPresent();
            InlineKeyboardButton[][] rows = markup.get().inlineKeyboard();
            assertThat(rows).hasDimensions(1, 2);
            assertThat(rows[0][0].text()).isEqualTo("Confirm");
            assertThat(rows[0][0].callbackData())
                    .isEqualTo(ProposalCallbackData.render(ProposalResolution.ACCEPT, reference));
            assertThat(rows[0][1].text()).isEqualTo("Delete");
            assertThat(rows[0][1].callbackData())
                    .isEqualTo(ProposalCallbackData.render(ProposalResolution.DISCARD, reference));
        }

        @Test
        @DisplayName(
                "when a PARTIAL report carries one summary and a reference - then returns the same one-row, two-button markup")
        void whenPartialReportCarriesOneSummaryAndReference_thenReturnsSameOneRowTwoButtonMarkup() {
            MessageReference reference = MessageReference.newReference();
            ProposalSummary summary =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            TurnReport report =
                    new TurnReport("555", "1", ReportOutcome.PARTIAL, List.of(summary), List.of(), reference);

            Optional<InlineKeyboardMarkup> markup = TurnReportRenderer.renderKeyboard(report);

            assertThat(markup).isPresent();
            InlineKeyboardButton[][] rows = markup.get().inlineKeyboard();
            assertThat(rows).hasDimensions(1, 2);
            assertThat(rows[0][0].text()).isEqualTo("Confirm");
            assertThat(rows[0][1].text()).isEqualTo("Delete");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("outcomesWithNoSummaries")
        @DisplayName("when a NOTHING_IDENTIFIED report and a FAILED report both have no summaries - then returns empty")
        void whenNothingIdentifiedAndFailedReportsHaveNoSummaries_thenReturnsEmpty(
                String description, ReportOutcome outcome) {
            TurnReport report =
                    new TurnReport("555", "1", outcome, List.of(), List.of(), MessageReference.newReference());

            Optional<InlineKeyboardMarkup> markup = TurnReportRenderer.renderKeyboard(report);

            assertThat(markup).isEmpty();
        }

        static Stream<Arguments> outcomesWithNoSummaries() {
            return Stream.of(
                    arguments("NOTHING_IDENTIFIED", ReportOutcome.NOTHING_IDENTIFIED),
                    arguments("FAILED", ReportOutcome.FAILED));
        }

        @Test
        @DisplayName(
                "when a RECORDED report's summary list is empty - then returns empty, the list deciding, not the outcome")
        void whenRecordedReportSummaryListIsEmpty_thenReturnsEmpty() {
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.RECORDED, List.of(), List.of(), MessageReference.newReference());

            Optional<InlineKeyboardMarkup> markup = TurnReportRenderer.renderKeyboard(report);

            assertThat(markup).isEmpty();
        }

        @Test
        @DisplayName(
                "when an ANSWERED report carries summaries and no proposal - then returns empty, since the buttons resolve proposals and there are none")
        void whenAnsweredReportCarriesSummariesAndNoProposal_thenReturnsEmpty() {
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            TurnReport report = new TurnReport(
                    "555", "1", ReportOutcome.ANSWERED, List.of(), List.of(summary), MessageReference.newReference());

            Optional<InlineKeyboardMarkup> markup = TurnReportRenderer.renderKeyboard(report);

            assertThat(markup).isEmpty();
        }

        @Test
        @DisplayName(
                "when a RECORDED report carries both a summary and a proposal - then returns the same one-row, two-button markup it does today")
        void whenRecordedReportCarriesSummaryAndProposal_thenReturnsSameOneRowTwoButtonMarkup() {
            MessageReference reference = MessageReference.newReference();
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
            SpendingSummary summary = new SpendingSummary(period, List.of(new CurrencyTotal(new Money(4230, EUR), 1)));
            ProposalSummary proposal =
                    new ProposalSummary("Groceries", "Food", "weekly shop", Optional.of("Rewe"), new Money(4230, EUR));
            TurnReport report =
                    new TurnReport("555", "1", ReportOutcome.RECORDED, List.of(proposal), List.of(summary), reference);

            Optional<InlineKeyboardMarkup> markup = TurnReportRenderer.renderKeyboard(report);

            assertThat(markup).isPresent();
            InlineKeyboardButton[][] rows = markup.get().inlineKeyboard();
            assertThat(rows).hasDimensions(1, 2);
            assertThat(rows[0][0].text()).isEqualTo("Confirm");
            assertThat(rows[0][0].callbackData())
                    .isEqualTo(ProposalCallbackData.render(ProposalResolution.ACCEPT, reference));
            assertThat(rows[0][1].text()).isEqualTo("Delete");
            assertThat(rows[0][1].callbackData())
                    .isEqualTo(ProposalCallbackData.render(ProposalResolution.DISCARD, reference));
        }
    }
}
