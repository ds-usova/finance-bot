package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidIncomingMessageException;

public class HandleIncomingMessageUseCase implements HandleIncomingMessagePort {

    private final InitializeUserPort initializeUserPort;
    private final CategoryRepository categoryRepository;
    private final IntentExtractionPort intentExtractionPort;
    private final Logger log;

    public HandleIncomingMessageUseCase(
            InitializeUserPort initializeUserPort,
            CategoryRepository categoryRepository,
            IntentExtractionPort intentExtractionPort,
            LoggerFactory loggerFactory) {
        this.initializeUserPort = initializeUserPort;
        this.categoryRepository = categoryRepository;
        this.intentExtractionPort = intentExtractionPort;
        this.log = loggerFactory.getLogger(HandleIncomingMessageUseCase.class);
    }

    @Override
    public void handle(HandleIncomingMessageCommand command) {
        if (command == null) {
            throw new InvalidIncomingMessageException("incoming message is absent");
        }
        // TODO: resolve the user via initializeUserPort, read their known categories via categoryRepository,
        // and call intentExtractionPort.extract with a request built from the command's text, those
        // categories, an empty default currency and the user's external id, per the design's steps 2-5.
        log.info("incoming message from conversation {}: {}", command.conversationId(), command.text());
    }
}
