package bot.finance.ai.common.rows;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/** Static helpers over a {@link JdbcTemplate}, for a test that reaches the {@code incoming_message} table directly. */
public final class IncomingMessageRowUtils {

    private IncomingMessageRowUtils() {}

    public static void insert(
            JdbcTemplate jdbcTemplate, long userId, String incomingMessageId, String text, Instant receivedAt) {
        jdbcTemplate.update(
                "INSERT INTO incoming_message (user_id, incoming_message_id, text, received_at) VALUES (?, ?, ?, ?)",
                userId,
                incomingMessageId,
                text,
                Timestamp.from(receivedAt));
    }

    public static int count(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                Integer.class,
                userId,
                incomingMessageId);
        return count == null ? 0 : count;
    }

    public static String text(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        return jdbcTemplate.queryForObject(
                "SELECT text FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                String.class,
                userId,
                incomingMessageId);
    }

    public static void deleteAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM incoming_message");
    }
}
