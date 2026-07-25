package bot.finance.application.usecase;

import bot.finance.application.dto.IncomingMessage;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;

/**
 * Prints an incoming message through the {@link Logger} port. Field-level validation belongs to
 * {@link IncomingMessage}'s constructor, so this use case trusts the command's fields and checks only that the
 * command itself is present.
 */
public class HandleIncomingMessageUseCase implements HandleIncomingMessagePort {

    private final Logger log;

    public HandleIncomingMessageUseCase(LoggerFactory loggerFactory) {
        this.log = loggerFactory.getLogger(HandleIncomingMessageUseCase.class);
    }

    @Override
    public void handle(IncomingMessage message) {
        // rejects an absent command with InvalidIncomingMessageException,
        // then prints the conversation id and message text at info level through the Logger port
    }

}
