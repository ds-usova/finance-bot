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
    }

    @Override
    public void end(EndTurnCommand command) {
        if (command == null) {
            throw new InvalidTurnEndException("turn end is absent");
        }
        // stub-intent: a reference naming a written message settles that text of the caller's, found by its id;
        // any other settles the Telegram turn kept under the caller and the reference; either settled with the
        // command's ending, and nothing happens when none is found; a store failure propagates
    }
}
