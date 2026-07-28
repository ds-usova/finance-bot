package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidIntentException;

public record UnknownIntent(String reason) implements Intent {

    public UnknownIntent {
        if (reason == null || reason.isBlank()) {
            throw new InvalidIntentException("Reason must not be null or blank");
        }
    }
}
