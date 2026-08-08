package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;

public record BrowseGroupingsCommand(AuthenticatedUserId userId) {

    public BrowseGroupingsCommand {
        if (userId == null) {
            throw new InvalidUserException("browse groupings command has no user id");
        }
    }
}
