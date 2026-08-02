package bot.finance.application.dto;

import java.util.List;

public record ProposalReport(
        String conversationId, String inboundMessageId, ReportOutcome outcome, List<ProposalSummary> proposals) {

    public ProposalReport {
        proposals = proposals == null ? List.of() : List.copyOf(proposals);
    }
}
