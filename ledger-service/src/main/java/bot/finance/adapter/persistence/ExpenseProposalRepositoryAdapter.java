package bot.finance.adapter.persistence;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.ProposalIds;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseProposalRepositoryAdapter implements ExpenseProposalRepository {

    private static final String CATEGORY_FOREIGN_KEY = "expense_proposal_category_id_fkey";
    private static final String USER_FOREIGN_KEY = "expense_proposal_user_id_fkey";

    private final ExpenseProposalEntityRepository expenseProposalEntityRepository;

    public ExpenseProposalRepositoryAdapter(ExpenseProposalEntityRepository expenseProposalEntityRepository) {
        this.expenseProposalEntityRepository = expenseProposalEntityRepository;
    }

    @Override
    @Transactional
    public ExpenseProposal create(ExpenseProposal proposal) {
        ColumnLimits.validateExpenseProposalText(
                proposal.description(), proposal.merchant().orElse(null));

        ExpenseProposalEntity saved;
        try {
            saved = expenseProposalEntityRepository.save(ExpenseProposalEntity.fromDomain(proposal));
        } catch (RuntimeException e) {
            throw classify(proposal, e);
        }
        return saved.toDomain();
    }

    @Override
    public List<ProposalSummary> findSummariesByMessageReference(long userId, IncomingMessageId reference) {
        try {
            return expenseProposalEntityRepository.findSummariesByMessageReference(userId, reference.value()).stream()
                    .map(ProposalSummaryProjection::toSummary)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find proposal summaries for user " + userId + " and message reference "
                            + reference.value(),
                    e);
        }
    }

    @Override
    @Transactional
    public int accept(long userId, IncomingMessageId reference, Instant now) {
        try {
            return expenseProposalEntityRepository.accept(
                    userId, reference.value(), now.truncatedTo(ChronoUnit.MICROS));
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to accept proposals for user " + userId + " and message reference " + reference.value(), e);
        }
    }

    @Override
    @Transactional
    public int discard(long userId, IncomingMessageId reference) {
        try {
            return expenseProposalEntityRepository.discard(userId, reference.value());
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to discard proposals for user " + userId + " and message reference " + reference.value(),
                    e);
        }
    }

    @Override
    @Transactional
    public List<IncomingMessageId> acceptByIds(long userId, ProposalIds ids, Instant now) {
        // runs the DELETE ... RETURNING / INSERT ... RETURNING chain narrowed by user_id and id IN (:ids),
        // over incoming_message_id, answering the incoming_message_id of every row it moved
        return null;
    }

    @Override
    public Set<IncomingMessageId> findWithPendingProposals(long userId, Collection<IncomingMessageId> ids) {
        // runs SELECT incoming_message_id, count(*) FROM expense_proposal WHERE user_id = :userId AND
        // incoming_message_id IN (:incomingMessageIds) GROUP BY incoming_message_id, answering the ids with a
        // nonzero count
        return null;
    }

    private static RuntimeException classify(ExpenseProposal proposal, RuntimeException e) {
        return switch (ForeignKeyViolations.constraintName(e)) {
            case CATEGORY_FOREIGN_KEY ->
                new EntityNotFoundException("category", "no category stored for id " + proposal.categoryId());
            case USER_FOREIGN_KEY -> new EntityNotFoundException("user", "no user stored for id " + proposal.userId());
            case null, default ->
                new PersistenceFailedException("failed to store expense proposal for user " + proposal.userId(), e);
        };
    }
}
