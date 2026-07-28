package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;

public record NewUser(String externalId) {

    public NewUser {
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidUserException("new user has no external id");
        }
    }

}
