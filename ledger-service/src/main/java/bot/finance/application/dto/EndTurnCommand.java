package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;

public record EndTurnCommand(AuthenticatedUserId userId, IncomingMessageId reference, TurnEnding ending) {

    public EndTurnCommand {
        // TODO: throw InvalidTurnEndException when the user id, the reference or the ending is absent
    }
}
