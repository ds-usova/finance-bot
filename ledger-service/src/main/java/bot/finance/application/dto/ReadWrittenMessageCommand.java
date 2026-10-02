package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;

public record ReadWrittenMessageCommand(AuthenticatedUserId userId, long messageId) {

    public ReadWrittenMessageCommand {
        // stub-intent: refuse an absent caller with InvalidUserException, and a message id below 1 with
        // InvalidWrittenMessageException naming `id`.
    }
}
