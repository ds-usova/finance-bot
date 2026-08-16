package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.SpendingRow;
import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcRecordedExpenseStoreAdapter implements RecordedExpenseStorePort {

    private final RecordedExpenseEntityRepository repository;
    private final IncomingMessageEntityRepository messageRepository;

    public JdbcRecordedExpenseStoreAdapter(
            RecordedExpenseEntityRepository repository, IncomingMessageEntityRepository messageRepository) {
        this.repository = repository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void recordProposed(SpendingRow proposal) {
        Optional<MessageIdentity> identity = proposal.messageIdentity();
        if (identity.isEmpty()) {
            return;
        }

        try {
            repository.upsertProposed(
                    identity.get().userId(),
                    identity.get().incomingMessageId(),
                    proposal.id(),
                    proposal.description(),
                    proposal.merchant().orElse(null),
                    proposal.amountMinorUnits(),
                    proposal.currencyCode().code(),
                    proposal.categoryId(),
                    proposal.categoryName().orElse(null),
                    proposal.groupingName().orElse(null),
                    Instant.now());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to record proposed expense");
        }
    }

    @Override
    @Transactional
    public void settleProposalDeleted(SpendingRow proposal, String transactionId) {
        Optional<MessageIdentity> identity = proposal.messageIdentity();
        if (identity.isEmpty()) {
            return;
        }

        try {
            Optional<Long> messageId = messageRepository.lockId(
                    identity.get().userId(), identity.get().incomingMessageId());
            if (messageId.isEmpty()) {
                return;
            }

            Optional<RecordedExpenseEntity> match = repository.findLowestLoneAcceptedByExpense(
                    messageId.get(),
                    transactionId,
                    proposal.description(),
                    proposal.merchant().orElse(null),
                    proposal.amountMinorUnits(),
                    proposal.currencyCode().code(),
                    proposal.categoryId());

            Instant now = Instant.now();
            if (match.isPresent()) {
                repository.findByProposalId(proposal.id()).ifPresent(repository::delete);
                repository.pairWithProposalId(match.get().id(), proposal.id(), now);
            } else {
                repository.markDiscarded(proposal.id(), transactionId, now);
            }
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to settle a proposal deletion");
        }
    }

    @Override
    @Transactional
    public void settleExpenseInserted(SpendingRow expense, String transactionId) {
        Optional<MessageIdentity> identity = expense.messageIdentity();
        if (identity.isEmpty()) {
            return;
        }

        try {
            Optional<Long> messageId = messageRepository.lockId(
                    identity.get().userId(), identity.get().incomingMessageId());
            if (messageId.isEmpty()) {
                return;
            }

            Optional<RecordedExpenseEntity> match = repository.findLowestDiscardedMatch(
                    messageId.get(),
                    transactionId,
                    expense.description(),
                    expense.merchant().orElse(null),
                    expense.amountMinorUnits(),
                    expense.currencyCode().code(),
                    expense.categoryId());

            if (match.isPresent()) {
                repository.pairWithExpenseId(match.get().id(), expense.id(), Instant.now());
            } else {
                repository.upsertLoneAccepted(
                        identity.get().userId(),
                        identity.get().incomingMessageId(),
                        expense.id(),
                        expense.description(),
                        expense.merchant().orElse(null),
                        expense.amountMinorUnits(),
                        expense.currencyCode().code(),
                        expense.categoryId(),
                        expense.categoryName().orElse(null),
                        expense.groupingName().orElse(null),
                        transactionId,
                        Instant.now());
            }
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to settle an expense insert");
        }
    }

    @Override
    public void refileExpense(SpendingRow expense) {
        try {
            repository.updateFiling(
                    expense.id(),
                    expense.categoryId(),
                    expense.categoryName().orElse(null),
                    expense.groupingName().orElse(null),
                    Instant.now());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to refile a recorded expense");
        }
    }

    @Override
    public void removeExpense(long expenseId) {
        try {
            repository.deleteByExpenseId(expenseId);
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to remove a recorded expense");
        }
    }

    @Override
    public void renameCategory(long categoryId, String name) {
        try {
            repository.renameCategory(categoryId, name, Instant.now());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to rename a category");
        }
    }

    @Override
    public void renameGrouping(long userId, String from, String to) {
        try {
            repository.renameGrouping(userId, from, to, Instant.now());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to rename a grouping");
        }
    }

    @Override
    public void abandonAcceptance(MessageIdentity message, String transactionId) {
        try {
            repository.markUnknown(message.userId(), message.incomingMessageId(), transactionId, Instant.now());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to abandon an acceptance");
        }
    }
}
