package bot.finance.application.usecase;

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
        // stub-intent: for an end or a refused hand-over. Reads the days and periods under the text's message and
        // settles it with the outcome the ending and those earn, unless it is already DONE; only when this call
        // settled it: logs at error an ending other than acted on, naming the message and the outcome, discards its
        // periods, logging a failed discard at warn, and counts the turn under its outcome. Answers whether this
        // call settled it
        return false;
    }
}
