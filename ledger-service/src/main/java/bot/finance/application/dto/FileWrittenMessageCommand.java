package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.AuthenticatedUserId;

public record FileWrittenMessageCommand(AuthenticatedUserId userId, String text) {

    public FileWrittenMessageCommand {
        if (userId == null) {
            throw new InvalidUserException("file written message command has no user id");
        }
        if (text == null) {
            throw new InvalidWrittenMessageException("file written message command has no text");
        }
    }
}
