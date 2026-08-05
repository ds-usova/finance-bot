package bot.finance.application.dto;

import bot.finance.domain.value.MessageReference;
import java.util.List;

public record ProposalReport(
        String conversationId,
        String inboundMessageId,
        ReportOutcome outcome,
        List<ProposalSummary> proposals,
        MessageReference reference) {

    public ProposalReport {
        proposals = proposals == null ? List.of() : List.copyOf(proposals);
    }
}
