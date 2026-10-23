package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.TurnHandOverDispatchPort;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.model.User;
import java.time.Clock;

public class HandleIncomingMessageUseCase implements HandleIncomingMessagePort {

    private final InitializeUserPort initializeUserPort;
    private final IncomingTurnRepository incomingTurnRepository;
    private final TurnHandOverDispatchPort turnHandOverDispatchPort;
    private final Clock clock;
    private final Logger log;

    public HandleIncomingMessageUseCase(
            InitializeUserPort initializeUserPort,
            IncomingTurnRepository incomingTurnRepository,
            TurnHandOverDispatchPort turnHandOverDispatchPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.initializeUserPort = initializeUserPort;
        this.incomingTurnRepository = incomingTurnRepository;
        this.turnHandOverDispatchPort = turnHandOverDispatchPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(HandleIncomingMessageUseCase.class);
    }

    @Override
    public void handle(HandleIncomingMessageCommand command) {
        if (command == null) {
            throw new InvalidIncomingMessageException("incoming message is absent");
        }
        User user = initializeUserPort.initialize(new InitializeUserCommand(command.userExternalId()));
        IncomingTurn turn = IncomingTurn.newTurn(
                user.id().orElseThrow(),
                command.conversationId(),
                command.inboundMessageId(),
                command.text(),
                clock.instant());
        if (!incomingTurnRepository.keep(turn)) {
            log.info("a turn is already kept for message {}", turn.reference());
            return;
        }
        turnHandOverDispatchPort.dispatch();
    }
}
