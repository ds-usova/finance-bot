package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.MessageIdentity;

public record AcceptTurnCommand(ExtractIntentsCommand turn, MessageIdentity turnKey) {

    public AcceptTurnCommand {
        if (turn == null) {
            throw new InvalidValueException("Turn must not be null");
        }
        if (turnKey == null) {
            throw new InvalidValueException("Turn key must not be null");
        }
    }
}
