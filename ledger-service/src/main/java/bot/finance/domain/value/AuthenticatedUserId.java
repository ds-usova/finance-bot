package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidUserException;

public record AuthenticatedUserId(String externalId) {

    public AuthenticatedUserId {
        if (externalId == null || externalId.isBlank()) {
            throw new InvalidUserException("externalId must not be absent, empty or whitespace-only");
        }
    }
}
