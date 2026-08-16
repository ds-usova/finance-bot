package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.RecordedChange;

public record LearnMessageOutcomeCommand(String deliveryId, RecordedChange change) {

    public LearnMessageOutcomeCommand {
        // TODO: refuse a null or blank deliveryId, or a null change, as InvalidValueException
    }
}
