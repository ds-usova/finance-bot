package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.Embedding;
import java.util.Optional;

public record RegisteredMessage(long messageId, Optional<Embedding> embedding) {

    public RegisteredMessage {
        if (embedding == null) {
            throw new InvalidValueException("Embedding must not be null");
        }
    }
}
