package bot.finance.ai.common.rows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
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

    /** The generated id of the row under {@code userId} and {@code incomingMessageId}. */
    public static long id(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                Long.class,
                userId,
                incomingMessageId);
        if (id == null) {
            throw new AssertionError("no incoming_message row for user " + userId + " and " + incomingMessageId);
        }
        return id;
    }

    public static int count(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                Integer.class,
                userId,
                incomingMessageId);
        return count == null ? 0 : count;
    }

    public static int countReceivedBefore(JdbcTemplate jdbcTemplate, long userId, Instant cut) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incoming_message WHERE user_id = ? AND received_at < ?",
                Integer.class,
                userId,
                Timestamp.from(cut));
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

    /** Inserts a row already carrying a vector, a received-at and an embedding-attempt count. */
    public static void insertWithVector(
            JdbcTemplate jdbcTemplate,
            long userId,
            String incomingMessageId,
            String text,
            Instant receivedAt,
            List<Float> embedding,
            int embeddingAttempts) {
        jdbcTemplate.update(
                """
                INSERT INTO incoming_message (user_id, incoming_message_id, text, received_at, embedding,
                                               embedding_attempts)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?)
                """,
                userId,
                incomingMessageId,
                text,
                Timestamp.from(receivedAt),
                vectorLiteral(embedding),
                embeddingAttempts);
    }

    public static List<Float> vector(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        String literal = jdbcTemplate.queryForObject(
                "SELECT embedding::text FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                String.class,
                userId,
                incomingMessageId);
        return literal == null ? List.of() : parseVectorLiteral(literal);
    }

    public static int embeddingAttempts(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT embedding_attempts FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                Integer.class,
                userId,
                incomingMessageId);
        return attempts == null ? 0 : attempts;
    }

    public static Instant backfillClaimedAt(JdbcTemplate jdbcTemplate, long userId, String incomingMessageId) {
        Timestamp claimedAt = jdbcTemplate.queryForObject(
                "SELECT backfill_claimed_at FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                Timestamp.class,
                userId,
                incomingMessageId);
        return claimedAt == null ? null : claimedAt.toInstant();
    }

    private static String vectorLiteral(List<Float> embedding) {
        return embedding.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static List<Float> parseVectorLiteral(String literal) {
        String trimmed = literal.substring(1, literal.length() - 1);
        if (trimmed.isBlank()) {
            return List.of();
        }
        return Arrays.stream(trimmed.split(",")).map(Float::parseFloat).toList();
    }
}
