package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.CategoryClassifierPort;
import bot.finance.ai.application.port.LedgerTurnPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.SpendingReadOutPort;
import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.value.ExtractionMode;
import bot.finance.ai.domain.value.MessageExample;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

public class ClassifierTurn implements ExtractionTurn {

    private final SpendingReadOutPort spendingReadOutPort;
    private final CategoryClassifierPort categoryClassifierPort;
    private final LedgerTurnPort ledgerTurnPort;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final double minConfidence;
    private final Logger log;

    public ClassifierTurn(
            SpendingReadOutPort spendingReadOutPort,
            CategoryClassifierPort categoryClassifierPort,
            LedgerTurnPort ledgerTurnPort,
            TurnMeters turnMeters,
            Clock clock,
            double minConfidence,
            LoggerFactory loggerFactory) {
        this.spendingReadOutPort = spendingReadOutPort;
        this.categoryClassifierPort = categoryClassifierPort;
        this.ledgerTurnPort = ledgerTurnPort;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.minConfidence = minConfidence;
        this.log = loggerFactory.getLogger(ClassifierTurn.class);
    }

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.CLASSIFIER;
    }

    @Override
    public void run(ExtractIntentsCommand command, Optional<List<MessageExample>> examples) {
        // stub-intent: reads the message out, drops and logs each unusable expense, asks the classifier once about
        // the usable ones with the message and the examples, chooses each one's category, then files each expense
        // in the user's order and records each period, going on past a refused call and stopping on any other
        // failure
    }
}
