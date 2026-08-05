package bot.finance.application.dto;

import bot.finance.domain.value.MessageReference;
import java.util.List;

public record TurnReport(
        String conversationId,
        String inboundMessageId,
        ReportOutcome outcome,
        List<ProposalSummary> proposals,
        List<SpendingSummary> summaries,
        MessageReference reference) {

    public TurnReport {
        proposals = proposals == null ? List.of() : List.copyOf(proposals);
        summaries = summaries == null ? List.of() : List.copyOf(summaries);
    }
}
