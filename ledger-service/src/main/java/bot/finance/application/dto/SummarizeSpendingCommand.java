package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;

public record SummarizeSpendingCommand(
        AuthenticatedUserId userId, IncomingMessageId reference, String from, String to) {

    public SummarizeSpendingCommand {
        if (userId == null) {
            throw new InvalidSpendingQueryException("User id must not be null");
        }
        if (reference == null) {
            throw new InvalidSpendingQueryException("Message reference must not be null");
        }
    }
}
