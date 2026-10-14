package bot.finance.application.usecase;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.TurnHandOverDispatchPort;
import bot.finance.domain.exception.InvalidIncomingMessageException;
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
        // stub-intent: resolves the person, keeps the message's turn queued at the clock's instant, and wakes the
        // hand-over; a turn already kept under the message is logged once at info and nothing else happens; a
        // store failure propagates
    }
}
