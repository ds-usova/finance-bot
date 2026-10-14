package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.MessageIdentity;

public record AcceptTurnCommand(ExtractIntentsCommand turn, MessageIdentity turnKey) {

    public AcceptTurnCommand {
        // TODO: throw InvalidValueException when turn or turnKey is null
    }
}
