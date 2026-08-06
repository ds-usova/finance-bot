package bot.finance.common.rows;

import bot.finance.adapter.persistence.ExpenseProposalEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class ExpenseProposalRowUtils {

    private ExpenseProposalRowUtils() {}

    public static List<ExpenseProposalEntity> expenseProposalRowsFor(
            JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(ExpenseProposalEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    public static ExpenseProposalEntity storedProposal(
            JdbcAggregateTemplate jdbcAggregateTemplate,
            long userId,
            long categoryId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            UUID messageReference,
            Instant createdAt) {
        return jdbcAggregateTemplate.insert(new ExpenseProposalEntity(
                null,
                userId,
                categoryId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                messageReference,
                createdAt,
                createdAt));
    }
}
