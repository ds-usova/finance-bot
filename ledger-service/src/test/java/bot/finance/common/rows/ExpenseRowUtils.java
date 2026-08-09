package bot.finance.common.rows;

import bot.finance.adapter.persistence.ExpenseEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class ExpenseRowUtils {

    private ExpenseRowUtils() {}

    public static List<ExpenseEntity> expenseRowsFor(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(ExpenseEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    public static ExpenseEntity storedExpense(
            JdbcAggregateTemplate jdbcAggregateTemplate,
            long userId,
            long categoryId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            String incomingMessageId,
            Instant createdAt) {
        return jdbcAggregateTemplate.insert(new ExpenseEntity(
                null,
                userId,
                categoryId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                incomingMessageId,
                createdAt,
                createdAt));
    }
}
