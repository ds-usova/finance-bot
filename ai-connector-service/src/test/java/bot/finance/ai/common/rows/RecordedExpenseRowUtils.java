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
            SELECT status, proposal_id, expense_id, category_id, category_name, grouping_name, moved_in_tx,
                   description, merchant, amount_minor_units, currency_code
            FROM recorded_expense
            """;

    private RecordedExpenseRowUtils() {}

    /**
     * One row's status, both ids, category id, both names, the transaction it moved in and its content columns.
     */
    public record RecordedExpenseRow(
            String status,
            Long proposalId,
            Long expenseId,
            long categoryId,
            String categoryName,
            String groupingName,
            String movedInTx,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode) {}

    public static void insert(
            JdbcTemplate jdbcTemplate,
            long messageId,
            long userId,
            Long proposalId,
            Long expenseId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String categoryName,
            String groupingName,
            String status,
            String movedInTx,
            Instant updatedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO recorded_expense (message_id, user_id, proposal_id, expense_id, description, merchant,
                                               amount_minor_units, currency_code, category_id, category_name,
                                               grouping_name, status, moved_in_tx, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                messageId,
                userId,
                proposalId,
                expenseId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId,
                categoryName,
                groupingName,
                status,
                movedInTx,
                Timestamp.from(updatedAt));
    }

    /**
     * Inserts a decided row — one carrying a proposal but no expense id, never moved in a transaction, updated
     * now — for a test that only cares about what an example is built from.
     */
    public static void insertDecided(
            JdbcTemplate jdbcTemplate,
            long messageId,
            long userId,
            long proposalId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String categoryName,
            String groupingName,
            String status) {
        insert(
                jdbcTemplate,
                messageId,
                userId,
                proposalId,
                null,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId,
                categoryName,
                groupingName,
                status,
                null,
                Instant.now());
    }

    public static Optional<RecordedExpenseRow> findByProposalId(JdbcTemplate jdbcTemplate, long proposalId) {
        return jdbcTemplate
                .query(SELECT + " WHERE proposal_id = ?", RecordedExpenseRowUtils::mapRow, proposalId)
                .stream()
                .findFirst();
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
                (Long) rs.getObject("proposal_id"),
                (Long) rs.getObject("expense_id"),
                rs.getLong("category_id"),
                rs.getString("category_name"),
                rs.getString("grouping_name"),
                rs.getString("moved_in_tx"),
                rs.getString("description"),
                rs.getString("merchant"),
                rs.getLong("amount_minor_units"),
                rs.getString("currency_code"));
    }
}
