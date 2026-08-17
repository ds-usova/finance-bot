package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.MessageIdentity;

public record RecallExamplesCommand(MessageIdentity identity, String text) {

    public RecallExamplesCommand {
        // TODO: refuse a null identity and a null or blank text, as InvalidValueException
    }
}
