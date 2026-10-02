package bot.finance.application.usecase;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SweepWrittenMessagesPort;
import bot.finance.application.port.WrittenMessageRepository;
import java.time.Clock;
import java.time.Duration;

public class SweepWrittenMessagesUseCase implements SweepWrittenMessagesPort {

    private final WrittenMessageRepository writtenMessageRepository;
    private final ExpenseRepository expenseRepository;
    private final Clock clock;
    private final Duration longestTurnLife;
    private final Logger log;

    public SweepWrittenMessagesUseCase(
            WrittenMessageRepository writtenMessageRepository,
            ExpenseRepository expenseRepository,
            Clock clock,
            Duration longestTurnLife,
            LoggerFactory loggerFactory) {
        this.writtenMessageRepository = writtenMessageRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
        this.longestTurnLife = longestTurnLife;
        this.log = loggerFactory.getLogger(SweepWrittenMessagesUseCase.class);
    }

    @Override
    public int sweep() {
        // stub-intent: settle every READING text last touched before now minus the turn's longest life, by what is
        // stored under its web:<id>, answering how many were settled. Logs a warning per settled text naming
        // web:<id>, the person and the outcome.
        return 0;
    }
}
