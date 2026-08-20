package bot.finance.ai.common.rows;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;

/** Static helpers over a {@link JdbcTemplate}, for a test that reaches the {@code stream_entry_failure} table directly. */
public final class StreamEntryFailureRowUtils {

    private StreamEntryFailureRowUtils() {}

    public static Integer attempts(JdbcTemplate jdbcTemplate, String entryId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempts FROM stream_entry_failure WHERE entry_id = ?", Integer.class, entryId);
    }

    public static void insert(
            JdbcTemplate jdbcTemplate, String entryId, int attempts, Instant firstFailedAt, String lastError) {
        jdbcTemplate.update(
                "INSERT INTO stream_entry_failure (entry_id, attempts, first_failed_at, last_error) VALUES (?, ?, ?, ?)",
                entryId,
                attempts,
                Timestamp.from(firstFailedAt),
                lastError);
    }

    public static void deleteAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM stream_entry_failure");
    }
}
