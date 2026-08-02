package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.KnownCategory;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.model.User;
import java.util.List;
import java.util.Optional;

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

        log.debug("handling message: {}", command.text());
        User user = initializeUserPort.initialize(new InitializeUserCommand(command.conversationId()));
        List<KnownCategory> knownCategories = categoryRepository.findKnownCategories(user.id().orElseThrow());
        intentExtractionPort.extract(new IntentExtractionRequest(
                command.text(), knownCategories, Optional.empty(), user.externalId()));

        log.debug("handled message for conversation {}", command.conversationId());
    }
}
