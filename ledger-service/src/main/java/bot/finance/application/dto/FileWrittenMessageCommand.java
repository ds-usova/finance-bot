package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;

public record FileWrittenMessageCommand(AuthenticatedUserId userId, String text) {

    public FileWrittenMessageCommand {
        // stub-intent: refuse an absent caller with InvalidUserException, and an absent text with
        // InvalidWrittenMessageException naming `text`.
    }
}
