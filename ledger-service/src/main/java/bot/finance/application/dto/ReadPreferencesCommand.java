package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;

public record ReadPreferencesCommand(AuthenticatedUserId userId) {

    public ReadPreferencesCommand {
        if (userId == null) {
            throw new InvalidUserException("read preferences command has no user id");
        }
    }
}
