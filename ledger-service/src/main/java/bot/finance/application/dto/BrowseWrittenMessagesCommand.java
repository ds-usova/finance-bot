package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;

public record BrowseWrittenMessagesCommand(AuthenticatedUserId userId) {

    public BrowseWrittenMessagesCommand {
        if (userId == null) {
            throw new InvalidUserException("browse written messages command has no user id");
        }
    }
}
