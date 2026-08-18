package bot.finance.adapter.persistence;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.ProposalIds;
import bot.finance.domain.value.SpendingPeriod;
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
public class ExpenseRepositoryAdapter implements ExpenseRepository {

    private static final String CATEGORY_FOREIGN_KEY = "expense_category_id_fkey";
    private static final String USER_FOREIGN_KEY = "expense_user_id_fkey";

    private final ExpenseEntityRepository expenseEntityRepository;
    private final LedgerEventOutbox ledgerEventOutbox;
    private final SpendingEventRenderer spendingEventRenderer;

    public ExpenseRepositoryAdapter(
            ExpenseEntityRepository expenseEntityRepository,
            LedgerEventOutbox ledgerEventOutbox,
            SpendingEventRenderer spendingEventRenderer) {
        this.expenseEntityRepository = expenseEntityRepository;
        this.ledgerEventOutbox = ledgerEventOutbox;
        this.spendingEventRenderer = spendingEventRenderer;
    }

    @Override
    @Transactional
    public Expense create(Expense expense) {
        ColumnLimits.validateExpenseText(
                expense.description(), expense.merchant().orElse(null));

        ExpenseEntity saved;
        try {
            saved = expenseEntityRepository.save(truncatedToMicros(expense));
        } catch (RuntimeException e) {
            throw classify(expense, e);
        }

        // TODO: read the row back through findEventRow, render it as a ProposalCreated or ExpenseRecorded
        //  event and hand it to the outbox, inserting then deleting it before returning
        return saved.toDomain();
    }

    @Override
    public List<ProposalSummary> findSummariesByMessageReference(long userId, IncomingMessageId reference) {
        try {
            return expenseEntityRepository.findSummariesByMessageReference(userId, reference.value()).stream()
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
            List<SpendingRowProjection> changed =
                    expenseEntityRepository.accept(userId, reference.value(), now.truncatedTo(ChronoUnit.MICROS));
            // TODO: render each changed row as a ProposalAccepted event and hand it to the outbox,
            //  inserting then deleting it before returning
            return changed.size();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to accept proposals for user " + userId + " and message reference " + reference.value(), e);
        }
    }

    @Override
    @Transactional
    public int discard(long userId, IncomingMessageId reference, Instant now) {
        try {
            List<SpendingRowProjection> changed = expenseEntityRepository.discard(userId, reference.value());
            // TODO: render each changed row as a ProposalDiscarded event stamped with now and hand it to
            //  the outbox, inserting then deleting it before returning
            return changed.size();
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
            List<SpendingRowProjection> changed =
                    expenseEntityRepository.acceptByIds(userId, ids.ids(), now.truncatedTo(ChronoUnit.MICROS));
            // TODO: render each changed row as a ProposalAccepted event and hand it to the outbox,
            //  inserting then deleting it before returning
            return changed.stream()
                    .map(row -> IncomingMessageId.of(row.incomingMessageId()))
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
            return expenseEntityRepository.findWithPendingProposals(userId, incomingMessageIds).stream()
                    .map(IncomingMessageId::of)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find messages with pending proposals for user " + userId, e);
        }
    }

    @Override
    public int countByMessageReference(long userId, IncomingMessageId reference) {
        try {
            return expenseEntityRepository.countByMessageReference(userId, reference.value());
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to count expenses for user " + userId + " and message reference " + reference.value(), e);
        }
    }

    @Override
    public List<CurrencyTotal> totalsByCurrency(long userId, SpendingPeriod period) {
        Instant from = periodStart(period);
        Instant toExclusive = periodEndExclusive(period);

        try {
            return expenseEntityRepository.totalsByCurrency(userId, from, toExclusive).stream()
                    .map(CurrencyTotalProjection::toCurrencyTotal)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to total expenses by currency for user " + userId + " and period " + period, e);
        }
    }

    @Override
    public List<ExpenseEntry> findPage(long userId, ExpenseFilter filter) {
        try {
            return expenseEntityRepository
                    .findPage(
                            userId,
                            statusName(filter.status()),
                            filter.categoryId(),
                            periodStart(filter.period()),
                            periodEndExclusive(filter.period()),
                            filter.limit(),
                            filter.offset())
                    .stream()
                    .map(ExpenseEntryProjection::toExpenseEntry)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find expense page for user " + userId, e);
        }
    }

    @Override
    public long countMatching(long userId, ExpenseFilter filter) {
        try {
            return expenseEntityRepository.countMatching(
                    userId,
                    statusName(filter.status()),
                    filter.categoryId(),
                    periodStart(filter.period()),
                    periodEndExclusive(filter.period()));
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to count expenses for user " + userId, e);
        }
    }

    @Override
    @Transactional
    public Optional<ExpenseEntry> refile(
            long userId, long entryId, long categoryId, ExpenseStatus status, Instant now) {
        try {
            Optional<SpendingRowProjection> changed = expenseEntityRepository.refile(
                    userId, entryId, categoryId, status.name(), now.truncatedTo(ChronoUnit.MICROS));
            // TODO: render the changed row as an ExpenseRefiled or ProposalRefiled event and hand it to
            //  the outbox, inserting then deleting it before returning
            return changed.map(projection -> projection.toExpenseEntry(status));
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to refile expense " + entryId + " for user " + userId, e);
        }
    }

    private static String statusName(ExpenseStatus status) {
        return status == null ? null : status.name();
    }

    private static Instant periodStart(SpendingPeriod period) {
        return period == null ? null : period.startInstant();
    }

    private static Instant periodEndExclusive(SpendingPeriod period) {
        return period == null ? null : period.endInstantExclusive();
    }

    private static RuntimeException classify(Expense expense, RuntimeException e) {
        return switch (ForeignKeyViolations.constraintName(e)) {
            case CATEGORY_FOREIGN_KEY ->
                new EntityNotFoundException("category", "no category stored for id " + expense.categoryId());
            case USER_FOREIGN_KEY -> new EntityNotFoundException("user", "no user stored for id " + expense.userId());
            case null, default ->
                new PersistenceFailedException("failed to store expense for user " + expense.userId(), e);
        };
    }

    // The column's microsecond precision does not round-trip nanosecond-precision instants: the
    // driver rounds rather than truncates. Truncating to microseconds before writing removes the
    // sub-microsecond remainder so the stored value is exact.
    private static ExpenseEntity truncatedToMicros(Expense expense) {
        ExpenseEntity mapped = ExpenseEntity.fromDomain(expense);
        return new ExpenseEntity(
                mapped.id(),
                mapped.userId(),
                mapped.categoryId(),
                mapped.description(),
                mapped.merchant(),
                mapped.amountMinorUnits(),
                mapped.currencyCode(),
                mapped.incomingMessageId(),
                mapped.createdAt().truncatedTo(ChronoUnit.MICROS),
                mapped.updatedAt().truncatedTo(ChronoUnit.MICROS),
                mapped.status());
    }
}
