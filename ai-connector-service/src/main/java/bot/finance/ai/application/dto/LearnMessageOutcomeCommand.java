package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.RecordedChange;

public record LearnMessageOutcomeCommand(String deliveryId, RecordedChange change) {

    public LearnMessageOutcomeCommand {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new InvalidValueException("Delivery id must not be null or blank");
        }
        if (change == null) {
            throw new InvalidValueException("Change must not be null");
        }
    }
}
