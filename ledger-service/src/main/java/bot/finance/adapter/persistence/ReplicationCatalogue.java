package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.PersistenceFailedException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Postgres's replication catalogue: what a slot is retaining, whether a publication exists, and the connection a
 * slot rebuild runs its statements on.
 */
@Component
public class ReplicationCatalogue {

    private static final String SELECT_SLOT_RETENTION_SQL =
            """
            SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retained_bytes, wal_status
            FROM pg_replication_slots
            WHERE slot_name = ?
            """;

    private final DataSource dataSource;
    private final Logger log;

    public ReplicationCatalogue(DataSource dataSource, LoggerFactory loggerFactory) {
        this.dataSource = dataSource;
        this.log = loggerFactory.getLogger(SlotRebuildSession.class);
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

    /**
     * Runs the sequence while one connection holds the slot's advisory lock, answering empty when another
     * connection already holds it. An advisory lock is session-scoped, so taking it, running the sequence and
     * releasing it on the same connection is what keeps a pooled session from being handed back still holding it.
     */
    public <T> Optional<T> underSlotLock(String slotName, Function<SlotRebuildSession, T> sequence) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAdvisoryLock(connection, slotName)) {
                return Optional.empty();
            }

            try {
                return Optional.of(sequence.apply(new SlotRebuildSession(connection, slotName, log)));
            } finally {
                releaseAdvisoryLock(connection, slotName);
            }
        } catch (SQLException e) {
            throw new PersistenceFailedException("failed to reach the database for the slot rebuild", e);
        }
    }

    private boolean tryAdvisoryLock(Connection connection, String slotName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
            statement.setString(1, slotName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void releaseAdvisoryLock(Connection connection, String slotName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            statement.setString(1, slotName);
            statement.execute();
        }
    }
}
