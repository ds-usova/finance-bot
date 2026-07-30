package bot.finance.adapter.persistence;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseRepositoryAdapter implements ExpenseRepository {

    private final ExpenseEntityRepository expenseEntityRepository;

    public ExpenseRepositoryAdapter(ExpenseEntityRepository expenseEntityRepository) {
        this.expenseEntityRepository = expenseEntityRepository;
    }

    @Override
    @Transactional
    public Expense create(Expense expense) {
        ColumnLimits.validateExpenseText(
                expense.description(), expense.merchant().orElse(null));

        try {
            // TODO: classify the failure: walk the cause chain for a SQLException with SQLState 23503; a
            // violation of expense_category_id_fkey becomes EntityNotFoundException("category", ...), one of
            // expense_user_id_fkey becomes EntityNotFoundException("user", ...), everything else stays
            // PersistenceFailedException; and the .toDomain() of the saved row moves out of the try, since
            // Expense's invariants would otherwise surface a row that violates them as a storage failure
            return expenseEntityRepository.save(truncatedToMicros(expense)).toDomain();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to store expense for user " + expense.userId(), e);
        }
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
                mapped.createdAt().truncatedTo(ChronoUnit.MICROS),
                mapped.updatedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
