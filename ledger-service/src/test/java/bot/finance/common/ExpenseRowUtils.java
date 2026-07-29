package bot.finance.common;

import bot.finance.adapter.persistence.ExpenseEntity;
import java.util.List;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class ExpenseRowUtils {

    private ExpenseRowUtils() {}

    public static List<ExpenseEntity> expenseRowsFor(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(ExpenseEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }
}
