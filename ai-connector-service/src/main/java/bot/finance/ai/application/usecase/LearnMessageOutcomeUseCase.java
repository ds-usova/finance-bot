package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.RecordedExpenseStorePort;

public class LearnMessageOutcomeUseCase implements LearnMessageOutcomePort {

    private final RecordedExpenseStorePort recordedExpenseStorePort;
    private final ChangeAttemptStorePort changeAttemptStorePort;
    private final int entryAttempts;
    private final Logger log;

    public LearnMessageOutcomeUseCase(
            RecordedExpenseStorePort recordedExpenseStorePort,
            ChangeAttemptStorePort changeAttemptStorePort,
            int entryAttempts,
            LoggerFactory loggerFactory) {
        // TODO: refuse a non-positive entryAttempts as InvalidValueException
        this.recordedExpenseStorePort = recordedExpenseStorePort;
        this.changeAttemptStorePort = changeAttemptStorePort;
        this.entryAttempts = entryAttempts;
        this.log = loggerFactory.getLogger(LearnMessageOutcomeUseCase.class);
    }

    @Override
    public LearnOutcome learn(LearnMessageOutcomeCommand command) {
        // dispatches the change to the store by kind and op, counts a store-answered failure and drops the
        // change at entryAttempts, retries an unreachable store uncounted
        return null;
    }
}
