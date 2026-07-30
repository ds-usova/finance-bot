package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;

public record InitializeUserCommand(String externalId) {

    public InitializeUserCommand {
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidUserException("new user has no external id");
        }
    }
}
