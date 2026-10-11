package bot.finance.common.rows;

import bot.finance.adapter.persistence.WrittenMessageEntity;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class WrittenMessageRowUtils {

    private WrittenMessageRowUtils() {}

    public static long storedWrittenMessageId(
            JdbcAggregateTemplate jdbcAggregateTemplate,
            long userId,
            String text,
            WrittenMessageStatus status,
            ReportOutcome outcome,
            Instant updatedAt) {
        return jdbcAggregateTemplate
                .insert(new WrittenMessageEntity(
                        null,
                        userId,
                        text,
                        status.name(),
                        outcome == null ? null : outcome.name(),
                        updatedAt,
                        updatedAt))
                .id();
    }

    public static WrittenMessageEntity writtenMessageRow(JdbcAggregateTemplate jdbcAggregateTemplate, long id) {
        return jdbcAggregateTemplate.findById(id, WrittenMessageEntity.class);
    }
}
