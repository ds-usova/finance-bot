package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import bot.finance.ai.domain.value.CategoryRow;
import bot.finance.ai.domain.value.CategoryRowChange;
import bot.finance.ai.domain.value.ChangeOperation;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.RecordedChange;
import bot.finance.ai.domain.value.SpendingKind;
import bot.finance.ai.domain.value.SpendingRowChange;
import java.util.Optional;

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
        if (entryAttempts <= 0) {
            throw new InvalidValueException("entryAttempts must be positive");
        }

        this.recordedExpenseStorePort = recordedExpenseStorePort;
        this.changeAttemptStorePort = changeAttemptStorePort;
        this.entryAttempts = entryAttempts;
        this.log = loggerFactory.getLogger(LearnMessageOutcomeUseCase.class);
    }

    @Override
    public LearnOutcome learn(LearnMessageOutcomeCommand command) {
        RecordedChange change = command.change();

        try {
            apply(change);
        } catch (MessageStoreUnavailableException e) {
            log.warn("Store unreachable, retrying delivery {} later: {}", command.deliveryId(), e.getMessage());
            return LearnOutcome.RETRY_LATER;
        } catch (MessageStoreFailedException e) {
            return onFailure(command, change, e);
        }

        changeAttemptStorePort.clear(command.deliveryId());
        return LearnOutcome.APPLIED;
    }

    private void apply(RecordedChange change) {
        switch (change) {
            case SpendingRowChange spendingChange -> applySpendingChange(spendingChange);
            case CategoryRowChange categoryChange -> applyCategoryChange(categoryChange);
        }
    }

    private void applySpendingChange(SpendingRowChange change) {
        if (change.messageIdentity().isEmpty()) {
            return;
        }

        switch (change.kind()) {
            case PROPOSAL -> applyProposalChange(change);
            case EXPENSE -> applyExpenseChange(change);
        }
    }

    private void applyProposalChange(SpendingRowChange change) {
        switch (change.op()) {
            case CREATED, UPDATED ->
                recordedExpenseStorePort.recordProposed(change.after().orElseThrow());
            case DELETED ->
                recordedExpenseStorePort.settleProposalDeleted(change.before().orElseThrow(), change.transactionId());
        }
    }

    private void applyExpenseChange(SpendingRowChange change) {
        switch (change.op()) {
            case CREATED ->
                recordedExpenseStorePort.settleExpenseInserted(change.after().orElseThrow(), change.transactionId());
            case UPDATED ->
                recordedExpenseStorePort.refileExpense(change.after().orElseThrow());
            case DELETED ->
                recordedExpenseStorePort.removeExpense(
                        change.before().orElseThrow().id());
        }
    }

    private void applyCategoryChange(CategoryRowChange change) {
        if (change.op() != ChangeOperation.UPDATED) {
            return;
        }

        CategoryRow before = change.before().orElseThrow();
        CategoryRow after = change.after().orElseThrow();
        if (before.name().equals(after.name())) {
            return;
        }

        if (after.parentId().isPresent()) {
            recordedExpenseStorePort.renameCategory(after.id(), after.name());
        } else {
            recordedExpenseStorePort.renameGrouping(after.userId(), before.name(), after.name());
        }
    }

    private LearnOutcome onFailure(
            LearnMessageOutcomeCommand command, RecordedChange change, MessageStoreFailedException e) {
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

        return drop(command, change, e);
    }

    private LearnOutcome drop(
            LearnMessageOutcomeCommand command, RecordedChange change, MessageStoreFailedException e) {
        log.error(
                "Dropping delivery {} after repeated failures on {} {} row {}: {}",
                command.deliveryId(),
                kindOf(change),
                opOf(change),
                rowId(change),
                e.getMessage());

        if (change instanceof SpendingRowChange spendingChange) {
            abandonAcceptanceIfExpenseCreated(command, spendingChange);
        }

        changeAttemptStorePort.clear(command.deliveryId());
        return LearnOutcome.DROPPED;
    }

    private Object kindOf(RecordedChange change) {
        return switch (change) {
            case SpendingRowChange spendingChange -> spendingChange.kind();
            case CategoryRowChange ignored -> "CATEGORY";
        };
    }

    private ChangeOperation opOf(RecordedChange change) {
        return switch (change) {
            case SpendingRowChange spendingChange -> spendingChange.op();
            case CategoryRowChange categoryChange -> categoryChange.op();
        };
    }

    private long rowId(RecordedChange change) {
        return switch (change) {
            case SpendingRowChange spendingChange -> spendingChange.row().id();
            case CategoryRowChange categoryChange ->
                categoryChange.after().or(categoryChange::before).orElseThrow().id();
        };
    }

    private void abandonAcceptanceIfExpenseCreated(LearnMessageOutcomeCommand command, SpendingRowChange change) {
        if (change.kind() != SpendingKind.EXPENSE || change.op() != ChangeOperation.CREATED) {
            return;
        }

        Optional<MessageIdentity> messageIdentity = change.messageIdentity();
        if (messageIdentity.isEmpty()) {
            return;
        }

        try {
            recordedExpenseStorePort.abandonAcceptance(messageIdentity.get(), change.transactionId());
        } catch (MessageStoreFailedException e) {
            log.warn("Failed to abandon acceptance for delivery {}: {}", command.deliveryId(), e.getMessage());
        }
    }
}
