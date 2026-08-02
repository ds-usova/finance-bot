package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalReport;

public final class ProposalReportUtils {

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
        // TODO RU06: implement the four outcome wordings and the 4000-character cut described above.
        throw new UnsupportedOperationException("render is not yet implemented");
    }
}
