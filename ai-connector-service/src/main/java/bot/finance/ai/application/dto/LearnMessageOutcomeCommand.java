package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;

public record LearnMessageOutcomeCommand(
        String deliveryId, StreamPosition position, RecordedStatus status, SpendingRow entry) {

    public LearnMessageOutcomeCommand {
        if (deliveryId == null || deliveryId.isBlank()) {
            throw new InvalidValueException("Delivery id must not be null or blank");
        }
        if (position == null) {
            throw new InvalidValueException("Position must not be null");
        }
        if (status == null) {
            throw new InvalidValueException("Status must not be null");
        }
        if (entry == null) {
            throw new InvalidValueException("Entry must not be null");
        }
    }
}
