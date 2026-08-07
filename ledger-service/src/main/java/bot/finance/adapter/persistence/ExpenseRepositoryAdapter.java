package bot.finance.adapter.persistence;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseRepositoryAdapter implements ExpenseRepository {

    private static final String CATEGORY_FOREIGN_KEY = "expense_category_id_fkey";
    private static final String USER_FOREIGN_KEY = "expense_user_id_fkey";

    private final ExpenseEntityRepository expenseEntityRepository;

    public ExpenseRepositoryAdapter(ExpenseEntityRepository expenseEntityRepository) {
        this.expenseEntityRepository = expenseEntityRepository;
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
        return saved.toDomain();
    }

    @Override
    public int countByMessageReference(long userId, MessageReference reference) {
        try {
            return expenseEntityRepository.countByMessageReference(userId, reference.value());
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to count expenses for user " + userId + " and message reference " + reference.value(), e);
        }
    }

    @Override
    public List<CurrencyTotal> totalsByCurrency(long userId, SpendingPeriod period) {
        Instant from = period.from().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive =
                period.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

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
        // reads one page of the UNION ALL over expense and expense_proposal, scoped to userId and narrowed by
        // filter, ordered created_at DESC, status, id DESC, through ExpenseEntityRepository
        return List.of();
    }

    @Override
    public long countMatching(long userId, ExpenseFilter filter) {
        // adds the two arms' counts, each scoped to userId and narrowed by the same predicates as findPage(),
        // ignoring filter's limit and offset, through ExpenseEntityRepository
        return 0;
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
                mapped.messageReference(),
                mapped.createdAt().truncatedTo(ChronoUnit.MICROS),
                mapped.updatedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
