package bot.finance.common.rows;

import bot.finance.adapter.persistence.IncomingTurnEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class IncomingTurnRowUtils {

    private IncomingTurnRowUtils() {}

    public static long storedTurnId(
            JdbcAggregateTemplate jdbcAggregateTemplate,
            long userId,
            String conversationId,
            String inboundMessageId,
            String text,
            Instant keptAt,
            Instant lastAttemptAt,
            Instant handedOverAt) {
        return jdbcAggregateTemplate
                .insert(new IncomingTurnEntity(
                        null,
                        userId,
                        conversationId + ":" + inboundMessageId,
                        text,
                        conversationId,
                        inboundMessageId,
                        keptAt,
                        lastAttemptAt,
                        handedOverAt))
                .id();
    }

    public static List<IncomingTurnEntity> turnRowsFor(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(IncomingTurnEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    public static Optional<IncomingTurnEntity> turnRow(JdbcAggregateTemplate jdbcAggregateTemplate, long id) {
        return Optional.ofNullable(jdbcAggregateTemplate.findById(id, IncomingTurnEntity.class));
    }
}
