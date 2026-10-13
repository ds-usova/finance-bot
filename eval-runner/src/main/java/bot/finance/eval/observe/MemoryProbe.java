package bot.finance.eval.observe;

import bot.finance.eval.EvalException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Reads the AI Connector's database to say whether a learning message's decision is available to recall: the
 * message carries an embedding, and each of its expenses carries the decided status.
 */
public final class MemoryProbe {

    private static final String READINESS =
            """
            SELECT im.embedding IS NOT NULL AS embedded,
                   (SELECT count(*)
                      FROM recorded_expense re
                     WHERE re.message_id = im.id
                       AND re.expense_id = ANY (?)
                       AND re.status = ?) AS decided
              FROM incoming_message im
             WHERE im.incoming_message_id = ?
            """;

    private final String jdbcUrl;
    private final String user;
    private final String password;

    public MemoryProbe(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    public boolean ready(String incomingMessageId, List<Long> expenseIds, String expectedStatus) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(READINESS)) {
                statement.setArray(1, connection.createArrayOf("bigint", expenseIds.toArray()));
                statement.setString(2, expectedStatus);
                statement.setString(3, incomingMessageId);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() && row.getBoolean("embedded") && row.getLong("decided") == expenseIds.size();
                }
            }
        } catch (SQLException e) {
            throw new EvalException("memory-readiness", "probe failed: " + e.getMessage(), null, e);
        }
    }
}
