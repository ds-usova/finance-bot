package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;

public record ReadSessionCommand(AuthenticatedUserId userId) {

    public ReadSessionCommand {
        if (userId == null) {
            throw new InvalidUserException("read session command has no userId");
        }
    }
}
