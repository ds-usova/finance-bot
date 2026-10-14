package bot.finance.application.usecase;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.WrittenMessageRepository;
import java.time.Clock;

class WrittenTurnOutcome {

    private final WrittenMessageRepository writtenMessageRepository;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final Logger log;

    WrittenTurnOutcome(
            WrittenMessageRepository writtenMessageRepository,
            ExpenseRepository expenseRepository,
            SpendingQueryRepository spendingQueryRepository,
            TurnMeters turnMeters,
            Clock clock,
            Logger log) {
        this.writtenMessageRepository = writtenMessageRepository;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.log = log;
    }

    boolean settle(WrittenMessageView message, TurnEnding ending) {
        boolean stored = !expenseRepository
                .findDaysByMessageReference(message.userId(), message.incomingMessageId())
                .isEmpty();
        boolean queried = !spendingQueryRepository
                .findPeriodsByMessageReference(message.userId(), message.incomingMessageId())
                .isEmpty();
        ReportOutcome outcome = outcomeOf(ending, stored, queried);
        if (!writtenMessageRepository.settle(message.id(), outcome, clock.instant())) {
            return false;
        }
        if (ending != TurnEnding.ACTED_ON) {
            log.error("written message {} ended {} and was settled as {}", message.id(), ending, outcome);
        }
        new ExtractionTurn(spendingQueryRepository, log).discardPeriods(message.userId(), message.incomingMessageId());
        turnMeters.countTurn(outcome);
        return true;
    }

    private ReportOutcome outcomeOf(TurnEnding ending, boolean stored, boolean queried) {
        if (ending != TurnEnding.ACTED_ON) {
            return stored ? ReportOutcome.PARTIAL : ReportOutcome.FAILED;
        }
        if (stored) {
            return ReportOutcome.RECORDED;
        }
        return queried ? ReportOutcome.ANSWERED : ReportOutcome.NOTHING_IDENTIFIED;
    }
}
