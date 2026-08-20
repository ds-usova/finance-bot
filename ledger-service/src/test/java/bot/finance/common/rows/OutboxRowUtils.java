package bot.finance.common.rows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class OutboxRowUtils {

    private OutboxRowUtils() {}

    public static List<OutboxRow> outboxRowsFor(JdbcTemplate jdbcTemplate, long userId) {
        return jdbcTemplate.query(
                "SELECT id, type, occurred_at, payload::text AS payload FROM outbox WHERE payload->>'userId' = ?",
                (rs, rowNum) -> new OutboxRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("type"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("payload")),
                String.valueOf(userId));
    }

    public static long outboxRowCount(JdbcTemplate jdbcTemplate) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Long.class);
        return count == null ? 0 : count;
    }

    public static void storedOutboxRow(
            JdbcTemplate jdbcTemplate, UUID id, String type, Instant occurredAt, String payload) {
        jdbcTemplate.update(
                "INSERT INTO outbox (id, type, occurred_at, payload) VALUES (?, ?, ?, ?::jsonb)",
                id,
                type,
                Timestamp.from(occurredAt),
                payload);
    }

    /** The same row under the smallest payload the reads here filter by - a user id and nothing else. */
    public static void storedOutboxRowFor(
            JdbcTemplate jdbcTemplate, UUID id, String type, Instant occurredAt, long userId) {
        storedOutboxRow(jdbcTemplate, id, type, occurredAt, "{\"userId\": %d}".formatted(userId));
    }

    /**
     * Empties the table. A class asserting on the whole outbox needs it: the capture tests commit their rows
     * outside any rolled-back transaction, since the engine only reads what the write-ahead log already holds.
     */
    public static void clearOutbox(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM outbox");
    }

    public record OutboxRow(UUID id, String type, Instant occurredAt, String payload) {}
}
