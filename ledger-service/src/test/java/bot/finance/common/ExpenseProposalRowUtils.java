package bot.finance.common;

import bot.finance.adapter.persistence.ExpenseProposalEntity;
import java.util.List;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class ExpenseProposalRowUtils {

    private ExpenseProposalRowUtils() {}

    public static List<ExpenseProposalEntity> expenseProposalRowsFor(
            JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(ExpenseProposalEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }
}
