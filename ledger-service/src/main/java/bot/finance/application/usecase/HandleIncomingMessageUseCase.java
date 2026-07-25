package bot.finance.application.usecase;

import bot.finance.application.dto.IncomingMessage;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidIncomingMessageException;

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
        if (message == null) {
            throw new InvalidIncomingMessageException("incoming message is absent");
        }
        log.info("incoming message from conversation {}: {}", message.conversationId(), message.text());
    }

}
