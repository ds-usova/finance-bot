package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

public record UnknownIntent(String reason) implements Intent {

    public UnknownIntent {
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("Reason must not be null or blank");
        }
    }

}
