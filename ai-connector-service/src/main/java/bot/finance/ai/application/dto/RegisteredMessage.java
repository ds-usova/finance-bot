package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.Embedding;
import java.util.Optional;

public record RegisteredMessage(long messageId, Optional<Embedding> embedding) {

    public RegisteredMessage {
        // TODO: refuse a null Optional for the embedding, as InvalidValueException
    }
}
