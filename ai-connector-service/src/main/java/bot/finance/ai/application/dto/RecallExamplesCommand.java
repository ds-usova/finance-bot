package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.MessageIdentity;

public record RecallExamplesCommand(MessageIdentity identity, String text) {

    public RecallExamplesCommand {
        if (identity == null) {
            throw new InvalidValueException("Identity must not be null");
        }
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Text must not be null or blank");
        }
    }
}
