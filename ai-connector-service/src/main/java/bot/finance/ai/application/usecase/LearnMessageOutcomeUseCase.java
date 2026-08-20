package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.ChangeStreamMeters;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;

public class LearnMessageOutcomeUseCase implements LearnMessageOutcomePort {

    private final RecordedExpenseStorePort recordedExpenseStorePort;
    private final ChangeAttemptStorePort changeAttemptStorePort;
    private final int entryAttempts;
    private final ChangeStreamMeters changeStreamMeters;
    private final Logger log;

    public LearnMessageOutcomeUseCase(
            RecordedExpenseStorePort recordedExpenseStorePort,
            ChangeAttemptStorePort changeAttemptStorePort,
            int entryAttempts,
            ChangeStreamMeters changeStreamMeters,
            LoggerFactory loggerFactory) {
        if (entryAttempts <= 0) {
            throw new InvalidValueException("entryAttempts must be positive");
        }

        this.recordedExpenseStorePort = recordedExpenseStorePort;
        this.changeAttemptStorePort = changeAttemptStorePort;
        this.entryAttempts = entryAttempts;
        this.changeStreamMeters = changeStreamMeters;
        this.log = loggerFactory.getLogger(LearnMessageOutcomeUseCase.class);
    }

    @Override
    public LearnOutcome learn(LearnMessageOutcomeCommand command) {
        try {
            apply(command);
        } catch (MessageStoreUnavailableException e) {
            log.warn("Store unreachable, retrying delivery {} later: {}", command.deliveryId(), e.getMessage());
            return LearnOutcome.RETRY_LATER;
        } catch (MessageStoreFailedException e) {
            return onFailure(command, e);
        }

        changeAttemptStorePort.clear(command.deliveryId());
        return LearnOutcome.APPLIED;
    }

    private void apply(LearnMessageOutcomeCommand command) {
        if (command.entry().messageIdentity().isEmpty()) {
            return;
        }

        recordedExpenseStorePort.apply(command.entry(), command.status(), command.position());
    }

    private LearnOutcome onFailure(LearnMessageOutcomeCommand command, MessageStoreFailedException e) {
        int attempts;
        try {
            attempts = changeAttemptStorePort.countFailure(command.deliveryId(), e.getMessage());
        } catch (MessageStoreUnavailableException ex) {
            log.warn(
                    "Attempt store unreachable, retrying delivery {} later: {}", command.deliveryId(), ex.getMessage());
            return LearnOutcome.RETRY_LATER;
        }

        if (attempts < entryAttempts) {
            return LearnOutcome.RETRY_LATER;
        }

        return drop(command, e);
    }

    private LearnOutcome drop(LearnMessageOutcomeCommand command, MessageStoreFailedException e) {
        // TODO: count the drop on changeStreamMeters.
        log.error(
                "Dropping delivery {} after repeated failures on {} entry {}: {}",
                command.deliveryId(),
                command.status(),
                command.entry().expenseId(),
                e.getMessage());

        changeAttemptStorePort.clear(command.deliveryId());
        return LearnOutcome.DROPPED;
    }
}
