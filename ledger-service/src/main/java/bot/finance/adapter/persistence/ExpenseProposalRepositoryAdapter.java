package bot.finance.adapter.persistence;

import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.ProposalIds;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
        try {
            return expenseProposalEntityRepository
                    .acceptByIds(userId, ids.ids(), now.truncatedTo(ChronoUnit.MICROS))
                    .stream()
                    .map(IncomingMessageId::of)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to accept proposals by id for user " + userId, e);
        }
    }

    @Override
    public Set<IncomingMessageId> findWithPendingProposals(long userId, Collection<IncomingMessageId> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }

        try {
            List<String> incomingMessageIds =
                    ids.stream().map(IncomingMessageId::value).toList();
            return expenseProposalEntityRepository.findWithPendingProposals(userId, incomingMessageIds).stream()
                    .map(IncomingMessageId::of)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find messages with pending proposals for user " + userId, e);
        }
    }

    @Override
    @Transactional
    public Optional<ExpenseEntry> refile(long userId, long entryId, long categoryId, Instant now) {
        try {
            return expenseProposalEntityRepository
                    .refile(userId, entryId, categoryId, now.truncatedTo(ChronoUnit.MICROS))
                    .map(projection -> projection.toExpenseEntry(ExpenseStatus.PENDING));
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to refile expense proposal " + entryId + " for user " + userId, e);
        }
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
