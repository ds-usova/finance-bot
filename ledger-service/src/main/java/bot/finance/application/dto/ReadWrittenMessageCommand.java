package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.AuthenticatedUserId;

public record ReadWrittenMessageCommand(AuthenticatedUserId userId, long messageId) {

    public ReadWrittenMessageCommand {
        if (userId == null) {
            throw new InvalidUserException("read written message command has no user id");
        }
        if (messageId < 1) {
            throw new InvalidWrittenMessageException("read written message command has an id below 1: " + messageId);
        }
    }
}
