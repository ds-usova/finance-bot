package bot.finance.application.usecase;

import bot.finance.application.dto.EndTurnCommand;
import bot.finance.application.port.EndTurnPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.InvalidTurnEndException;
import java.time.Clock;
import java.util.Optional;

public class EndTurnUseCase implements EndTurnPort {

    private final IncomingTurnRepository incomingTurnRepository;
    private final WrittenMessageRepository writtenMessageRepository;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final ProposalReportRepository proposalReportRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final Logger log;
    private final WrittenTurnOutcome writtenTurnOutcome;
    private final IncomingTurnReport incomingTurnReport;

    public EndTurnUseCase(
            IncomingTurnRepository incomingTurnRepository,
            WrittenMessageRepository writtenMessageRepository,
            ExpenseRepository expenseRepository,
            SpendingQueryRepository spendingQueryRepository,
            MessageDeliveryPort messageDeliveryPort,
            ProposalReportRepository proposalReportRepository,
            TurnMeters turnMeters,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.incomingTurnRepository = incomingTurnRepository;
        this.writtenMessageRepository = writtenMessageRepository;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.proposalReportRepository = proposalReportRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.log = loggerFactory.getLogger(EndTurnUseCase.class);
        this.writtenTurnOutcome = new WrittenTurnOutcome(
                writtenMessageRepository, expenseRepository, spendingQueryRepository, turnMeters, clock, log);
        this.incomingTurnReport = new IncomingTurnReport(
                incomingTurnRepository,
                expenseRepository,
                spendingQueryRepository,
                messageDeliveryPort,
                proposalReportRepository,
                turnMeters,
                log);
    }

    @Override
    public void end(EndTurnCommand command) {
        if (command == null) {
            throw new InvalidTurnEndException("turn end is absent");
        }
        long userId = command.userId().userId();
        Optional<Long> writtenMessageId = command.reference().writtenMessageId();
        if (writtenMessageId.isPresent()) {
            writtenMessageRepository
                    .findForUser(userId, writtenMessageId.get())
                    .ifPresent(message -> writtenTurnOutcome.settle(message, command.ending()));
            return;
        }
        incomingTurnRepository
                .findFor(userId, command.reference())
                .ifPresent(turn -> incomingTurnReport.settle(turn, command.ending()));
    }
}
