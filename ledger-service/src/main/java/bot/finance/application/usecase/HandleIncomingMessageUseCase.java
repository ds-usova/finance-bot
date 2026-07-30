package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidIncomingMessageException;

public class HandleIncomingMessageUseCase implements HandleIncomingMessagePort {

    private final Logger log;

    public HandleIncomingMessageUseCase(LoggerFactory loggerFactory) {
        this.log = loggerFactory.getLogger(HandleIncomingMessageUseCase.class);
    }

    @Override
    public void handle(HandleIncomingMessageCommand command) {
        if (command == null) {
            throw new InvalidIncomingMessageException("incoming message is absent");
        }
        log.info("incoming message from conversation {}: {}", command.conversationId(), command.text());
    }
}
