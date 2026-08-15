package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidUserException;

public record AuthenticatedUserId(long userId) {

    public AuthenticatedUserId {
        if (userId <= 0) {
            throw new InvalidUserException("userId must be positive");
        }
    }

    public static AuthenticatedUserId of(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new InvalidUserException("subject must not be absent, empty or whitespace-only");
        }
        try {
            return new AuthenticatedUserId(Long.parseLong(subject));
        } catch (NumberFormatException e) {
            throw new InvalidUserException("subject must be a valid numeric user id");
        }
    }
}
