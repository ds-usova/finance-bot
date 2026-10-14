package bot.finance.application.usecase;

import bot.finance.application.dto.TurnChannel;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.SweepIncomingTurnsPort;
import bot.finance.application.port.TurnMeters;
import bot.finance.domain.model.IncomingTurn;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
    private final IncomingTurnReport incomingTurnReport;

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
    public int sweep() {
        Instant now = clock.instant();
        Duration oldest = incomingTurnRepository
                .oldestQueuedKeptAt()
                .map(keptAt -> Duration.between(keptAt, now))
                .orElse(Duration.ZERO);
        turnMeters.recordOldestQueued(TurnChannel.INCOMING, oldest);
        int swept = 0;
        for (IncomingTurn turn : incomingTurnRepository.findHandedOverBefore(now.minus(longestTurnLife))) {
            if (settle(turn)) {
                swept++;
            }
        }
        for (IncomingTurn turn :
                incomingTurnRepository.findQueuedForSweep(now.minus(maxQueueWait), now.minus(longestTurnLife))) {
            if (settle(turn)) {
                swept++;
            }
        }
        return swept;
    }

    private boolean settle(IncomingTurn turn) {
        if (!incomingTurnReport.settleSwept(turn)) {
            return false;
        }
        turnMeters.countSwept(TurnChannel.INCOMING);
        log.warn(
                "swept stale incoming turn for message {} of user {}",
                turn.reference().value(),
                turn.userId());
        return true;
    }
}
