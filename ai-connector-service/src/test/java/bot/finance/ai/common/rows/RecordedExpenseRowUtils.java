package bot.finance.ai.common.rows;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Static helpers over a {@link JdbcTemplate}, for a test that reaches the {@code recorded_expense} table directly. */
public final class RecordedExpenseRowUtils {

    private static final String SELECT =
            """
            SELECT status, expense_id, category_id, category_name, grouping_id, grouping_name,
                   description, merchant, amount, currency_code, applied_ms, applied_seq
            FROM recorded_expense
            """;

    private RecordedExpenseRowUtils() {}

    /**
     * One row's status, its expense id, its category and grouping columns, its content columns and the
     * position it was last applied from.
     */
    public record RecordedExpenseRow(
            String status,
            long expenseId,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long appliedMs,
            long appliedSeq) {}

    public static void insert(
            JdbcTemplate jdbcTemplate,
            long messageId,
            long userId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName,
            String status,
            long appliedMs,
            long appliedSeq,
            Instant updatedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO recorded_expense (message_id, user_id, expense_id, description, merchant, amount,
                                               currency_code, category_id, category_name, grouping_id,
                                               grouping_name, status, applied_ms, applied_seq, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                messageId,
                userId,
                expenseId,
                description,
                merchant,
                amount,
                currencyCode,
                categoryId,
                categoryName,
                groupingId,
                groupingName,
                status,
                appliedMs,
                appliedSeq,
                Timestamp.from(updatedAt));
    }

    /**
     * Inserts an applied row - a status and a position of its own, updated now - for a test that only cares
     * about what an example is built from.
     */
    public static void insertApplied(
            JdbcTemplate jdbcTemplate,
            long messageId,
            long userId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName,
            String status,
            long appliedMs,
            long appliedSeq) {
        insert(
                jdbcTemplate,
                messageId,
                userId,
                expenseId,
                description,
                merchant,
                amount,
                currencyCode,
                categoryId,
                categoryName,
                groupingId,
                groupingName,
                status,
                appliedMs,
                appliedSeq,
                Instant.now());
    }

    public static Optional<RecordedExpenseRow> findByExpenseId(JdbcTemplate jdbcTemplate, long expenseId) {
        return jdbcTemplate.query(SELECT + " WHERE expense_id = ?", RecordedExpenseRowUtils::mapRow, expenseId).stream()
                .findFirst();
    }

    public static int countByMessage(JdbcTemplate jdbcTemplate, long messageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recorded_expense WHERE message_id = ?", Integer.class, messageId);
        return count == null ? 0 : count;
    }

    public static void deleteAll(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM recorded_expense");
    }

    private static RecordedExpenseRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RecordedExpenseRow(
                rs.getString("status"),
                rs.getLong("expense_id"),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                (Long) rs.getObject("grouping_id"),
                rs.getString("grouping_name"),
                rs.getString("description"),
                rs.getString("merchant"),
                rs.getString("amount"),
                rs.getString("currency_code"),
                rs.getLong("applied_ms"),
                rs.getLong("applied_seq"));
    }
}
