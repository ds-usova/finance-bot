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
