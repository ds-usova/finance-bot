package bot.finance.adapter.telegram;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.dto.SpendingSummary;
import bot.finance.application.dto.TurnReport;
import bot.finance.domain.value.SpendingPeriod;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TurnReportRenderer {

    private static final int MAX_LENGTH = 4000;
    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private TurnReportRenderer() {}

    public static Optional<InlineKeyboardMarkup> renderKeyboard(TurnReport report) {
        if (report.proposals().isEmpty()) {
            return Optional.empty();
        }
        InlineKeyboardButton confirm = new InlineKeyboardButton("Confirm")
                .callbackData(ProposalCallbackData.render(ProposalResolution.ACCEPT, report.reference()));
        InlineKeyboardButton delete = new InlineKeyboardButton("Delete")
                .callbackData(ProposalCallbackData.render(ProposalResolution.DISCARD, report.reference()));
        return Optional.of(new InlineKeyboardMarkup(new InlineKeyboardButton[] {confirm, delete}));
    }

    /** No character is escaped, since {@link TelegramMessageDeliveryAdapter} sets no {@code parse_mode}. */
    public static String render(TurnReport report) {
        String summaryBlock = renderSummaries(report.summaries());
        int remainingBudget =
                summaryBlock.isEmpty() ? MAX_LENGTH : MAX_LENGTH - summaryBlock.length() - BLOCK_SEPARATOR.length();

        String rest =
                switch (report.outcome()) {
                    case ANSWERED -> "";
                    case NOTHING_IDENTIFIED -> "No expense was identified in that message.";
                    case FAILED -> "Something went wrong and nothing was noted — please try again.";
                    case RECORDED ->
                        renderList(recordedHeader(report.proposals().size()), report.proposals(), remainingBudget);
                    case PARTIAL ->
                        renderList(
                                "Something went wrong, so this may be incomplete. What I could read:",
                                report.proposals(),
                                remainingBudget);
                };

        if (summaryBlock.isEmpty()) {
            return rest;
        }
        if (rest.isEmpty()) {
            return summaryBlock;
        }
        return summaryBlock + BLOCK_SEPARATOR + rest;
    }

    private static String recordedHeader(int count) {
        return "Noted %d expense%s, pending your confirmation:".formatted(count, count == 1 ? "" : "s");
    }

    private static String renderList(String header, List<ProposalSummary> proposals, int maxLength) {
        StringBuilder text = new StringBuilder(header);
        int included = 0;
        for (int i = 0; i < proposals.size(); i++) {
            String bullet = "\n" + toBullet(proposals.get(i));
            boolean isLast = i == proposals.size() - 1;
            String omittedLine = isLast ? "" : omittedLine(proposals.size() - included - 1);
            if (text.length() + bullet.length() + omittedLine.length() > maxLength) {
                break;
            }
            text.append(bullet);
            included++;
        }
        if (included < proposals.size()) {
            text.append(omittedLine(proposals.size() - included));
        }
        return text.toString();
    }

    private static String omittedLine(int omittedCount) {
        return "\n… and " + omittedCount + " more.";
    }

    private static String renderSummaries(List<SpendingSummary> summaries) {
        if (summaries.isEmpty()) {
            return "";
        }
        List<String> blocks =
                summaries.stream().map(TurnReportRenderer::toSummaryBlock).toList();

        int included = 0;
        int usedLength = 0;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            int additionLength = blocks.get(i).length() + (included == 0 ? 0 : BLOCK_SEPARATOR.length());
            String omittedLine = i == 0 ? "" : omittedPeriodsLine(i);
            int omittedLineLength = omittedLine.isEmpty() ? 0 : omittedLine.length() + BLOCK_SEPARATOR.length();
            if (usedLength + additionLength + omittedLineLength > MAX_LENGTH) {
                break;
            }
            usedLength += additionLength;
            included++;
        }

        List<String> kept = blocks.subList(blocks.size() - included, blocks.size());
        StringBuilder text = new StringBuilder(String.join(BLOCK_SEPARATOR, kept));
        if (included < blocks.size()) {
            text.append(BLOCK_SEPARATOR).append(omittedPeriodsLine(blocks.size() - included));
        }
        return text.toString();
    }

    private static String omittedPeriodsLine(int omittedCount) {
        return "… and " + omittedCount + " more periods.";
    }

    private static String toSummaryBlock(SpendingSummary summary) {
        SpendingPeriod period = summary.period();
        String from = period.from().format(DATE_FORMAT);
        String to = period.to().format(DATE_FORMAT);
        if (summary.totals().isEmpty()) {
            return "Nothing is recorded between %s and %s.".formatted(from, to);
        }

        StringBuilder text = new StringBuilder("Between %s and %s you spent:".formatted(from, to));
        for (CurrencyTotal total : summary.totals()) {
            text.append("\n").append(toSummaryBullet(total));
        }
        return text.toString();
    }

    private static String toSummaryBullet(CurrencyTotal total) {
        int count = total.expenseCount();
        return "• %s %s (%d expense%s)"
                .formatted(total.total().amount(), total.total().currencyCode().code(), count, count == 1 ? "" : "s");
    }

    private static String toBullet(ProposalSummary proposal) {
        String merchant = proposal.merchant().map(name -> ", " + name).orElse("");
        return "• %s (%s) — %s%s: %s %s"
                .formatted(
                        proposal.categoryName(),
                        proposal.groupingName(),
                        proposal.description(),
                        merchant,
                        proposal.money().amount(),
                        proposal.money().currencyCode().code());
    }
}
