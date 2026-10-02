package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;

public record BrowseWrittenMessagesCommand(AuthenticatedUserId userId) {

    public BrowseWrittenMessagesCommand {
        // stub-intent: refuse an absent caller with InvalidUserException.
    }
}
