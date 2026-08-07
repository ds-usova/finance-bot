package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;

public record BrowseCategoriesCommand(AuthenticatedUserId userId, Long groupingId) {

    public BrowseCategoriesCommand {
        if (userId == null) {
            throw new InvalidUserException("browse categories command has no user id");
        }
    }
}
