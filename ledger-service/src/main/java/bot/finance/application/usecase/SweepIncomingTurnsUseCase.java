package bot.finance.application.usecase;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.SweepIncomingTurnsPort;
import bot.finance.application.port.TurnMeters;
import java.time.Clock;
import java.time.Duration;

public class SweepIncomingTurnsUseCase implements SweepIncomingTurnsPort {

    private final IncomingTurnRepository incomingTurnRepository;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final ProposalReportRepository proposalReportRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final Duration longestTurnLife;
    private final Duration maxQueueWait;
    private final Logger log;

    public SweepIncomingTurnsUseCase(
            IncomingTurnRepository incomingTurnRepository,
            ExpenseRepository expenseRepository,
            SpendingQueryRepository spendingQueryRepository,
            MessageDeliveryPort messageDeliveryPort,
            ProposalReportRepository proposalReportRepository,
            TurnMeters turnMeters,
            Clock clock,
            Duration longestTurnLife,
            Duration maxQueueWait,
            LoggerFactory loggerFactory) {
        this.incomingTurnRepository = incomingTurnRepository;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.proposalReportRepository = proposalReportRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.longestTurnLife = longestTurnLife;
        this.maxQueueWait = maxQueueWait;
        this.log = loggerFactory.getLogger(SweepIncomingTurnsUseCase.class);
    }

    @Override
    public int sweep() {
        // stub-intent: records the age of the oldest queued turn, zero when none; then settles as failed every
        // turn handed over before the longest life and every turn queued past the wait whose latest attempt is
        // older than the longest life, logging one warning naming the message and the person and counting it swept
        // for each turn this call settled; answers how many it settled
        return 0;
    }
}
