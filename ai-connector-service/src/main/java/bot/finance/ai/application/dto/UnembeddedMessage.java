package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;

public record UnembeddedMessage(long messageId, String text) {

    public UnembeddedMessage {
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Text must not be null or blank");
        }
    }
}
