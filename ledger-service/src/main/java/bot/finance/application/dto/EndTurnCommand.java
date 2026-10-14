package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidTurnEndException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;

public record EndTurnCommand(AuthenticatedUserId userId, IncomingMessageId reference, TurnEnding ending) {

    public EndTurnCommand {
        if (userId == null) {
            throw new InvalidTurnEndException("user id must be present");
        }
        if (reference == null) {
            throw new InvalidTurnEndException("reference must be present");
        }
        if (ending == null) {
            throw new InvalidTurnEndException("ending must be present");
        }
    }
}
