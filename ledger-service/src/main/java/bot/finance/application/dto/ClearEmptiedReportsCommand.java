package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidProposalReportException;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;
import java.util.Objects;

public record ClearEmptiedReportsCommand(long userId, List<IncomingMessageId> incomingMessageIds) {

    public ClearEmptiedReportsCommand {
        if (incomingMessageIds == null) {
            throw new InvalidProposalReportException("clear-emptied-reports command has no incoming message ids");
        }
        // Not contains(null): an immutable list answers that with a NullPointerException of its own, which is
        // the very thing this constructor exists to replace.
        if (incomingMessageIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidProposalReportException("clear-emptied-reports command has a null incoming message id");
        }
        incomingMessageIds = List.copyOf(incomingMessageIds);
    }
}
