package bot.finance.adapter.persistence;

import bot.finance.domain.exception.PersistenceFailedException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Postgres's replication catalogue: what a slot is retaining, and whether a publication exists. */
@Component
public class ReplicationCatalogue {

    private static final String SELECT_SLOT_RETENTION_SQL =
            """
            SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retained_bytes, wal_status
            FROM pg_replication_slots
            WHERE slot_name = ?
            """;

    private final DataSource dataSource;

    public ReplicationCatalogue(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<ReplicationSlotRetention> findSlotRetention(String slotName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SLOT_RETENTION_SQL)) {
            statement.setString(1, slotName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ReplicationSlotRetention(
                        resultSet.getLong("retained_bytes"), resultSet.getString("wal_status")));
            }
        } catch (SQLException e) {
            throw new PersistenceFailedException("failed to read the replication slot " + slotName, e);
        }
    }

    public boolean publicationExists(String publicationName) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM pg_publication WHERE pubname = ?")) {
            statement.setString(1, publicationName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new PersistenceFailedException("failed to read the publication " + publicationName, e);
        }
    }
}
