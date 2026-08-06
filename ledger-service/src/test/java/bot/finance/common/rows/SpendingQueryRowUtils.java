package bot.finance.common.rows;

import bot.finance.adapter.persistence.SpendingQueryEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class SpendingQueryRowUtils {

    private SpendingQueryRowUtils() {}

    public static List<SpendingQueryEntity> spendingQueryRowsFor(
            JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(SpendingQueryEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    public static SpendingQueryEntity storedQuery(
            JdbcAggregateTemplate jdbcAggregateTemplate,
            long userId,
            UUID messageReference,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant createdAt) {
        return jdbcAggregateTemplate.insert(
                new SpendingQueryEntity(null, userId, messageReference, periodStart, periodEnd, createdAt));
    }
}
