package bot.finance.adapter.persistence;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import java.sql.SQLException;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseRepositoryAdapter implements ExpenseRepository {

    // Postgres SQLState for a foreign key violation.
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CATEGORY_FOREIGN_KEY = "expense_category_id_fkey";
    private static final String USER_FOREIGN_KEY = "expense_user_id_fkey";
    private static final Pattern CONSTRAINT_NAME_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

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

    private static RuntimeException classify(Expense expense, RuntimeException e) {
        String constraint = foreignKeyConstraintName(e);
        if (CATEGORY_FOREIGN_KEY.equals(constraint)) {
            return new EntityNotFoundException("category", "no category stored for id " + expense.categoryId());
        }
        if (USER_FOREIGN_KEY.equals(constraint)) {
            return new EntityNotFoundException("user", "no user stored for id " + expense.userId());
        }
        return new PersistenceFailedException("failed to store expense for user " + expense.userId(), e);
    }

    // The constraint name is not exposed as a structured field anywhere in the exception chain -
    // Postgres reports it only inside the SQLException's message text.
    private static String foreignKeyConstraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && FOREIGN_KEY_VIOLATION.equals(sqlException.getSQLState())) {
                return constraintNameFromMessage(sqlException.getMessage());
            }
        }
        return null;
    }

    private static String constraintNameFromMessage(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = CONSTRAINT_NAME_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
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
