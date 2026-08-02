package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ProposalSummary;
import java.util.List;

public final class ProposalReportUtils {

    private static final int MAX_LENGTH = 4000;

    private ProposalReportUtils() {}

    /**
     * Renders a {@link ProposalReport} into the message text a user reads. {@code RECORDED} opens
     * "Noted N expense(s), pending your confirmation:" followed by one bullet per proposal in order;
     * {@code NOTHING_IDENTIFIED} renders "No expense was identified in that message."; {@code FAILED} renders
     * "Something went wrong and nothing was noted — please try again."; {@code PARTIAL} opens "Something went
     * wrong, so this may be incomplete. What I could read:" above the same bullet list. The bullets are cut once
     * the text would exceed 4000 characters, ending with "… and N more.". No character is escaped, since no
     * {@code parse_mode} is set on delivery.
     */
    public static String render(ProposalReport report) {
        return switch (report.outcome()) {
            case NOTHING_IDENTIFIED -> "No expense was identified in that message.";
            case FAILED -> "Something went wrong and nothing was noted — please try again.";
            case RECORDED -> renderList(recordedHeader(report.proposals().size()), report.proposals());
            case PARTIAL -> renderList(
                    "Something went wrong, so this may be incomplete. What I could read:", report.proposals());
        };
    }

    private static String recordedHeader(int count) {
        return "Noted %d expense%s, pending your confirmation:".formatted(count, count == 1 ? "" : "s");
    }

    private static String renderList(String header, List<ProposalSummary> proposals) {
        StringBuilder text = new StringBuilder(header);
        int included = 0;
        for (int i = 0; i < proposals.size(); i++) {
            String bullet = "\n" + toBullet(proposals.get(i));
            boolean isLast = i == proposals.size() - 1;
            String omittedLine = isLast ? "" : omittedLine(proposals.size() - included - 1);
            if (text.length() + bullet.length() + omittedLine.length() > MAX_LENGTH) {
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

    private static String toBullet(ProposalSummary proposal) {
        String merchant = proposal.merchant().map(name -> ", " + name).orElse("");
        return "• %s (%s) — %s%s: %s %s"
                .formatted(
                        proposal.categoryName(),
                        proposal.parentCategoryName(),
                        proposal.description(),
                        merchant,
                        proposal.money().amount(),
                        proposal.money().currencyCode().code());
    }
}
