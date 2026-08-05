package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.MessageReference;

public record SummarizeSpendingCommand(AuthenticatedUserId userId, MessageReference reference, String from, String to) {

    public SummarizeSpendingCommand {
        // TODO(RU03): self-validate — refuse an absent userId or reference with
        // InvalidSpendingQueryException
    }
}
