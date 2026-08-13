package bot.finance.adapter.cdc;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Rebuilds an invalidated replication slot: stops {@link ChangeStreamReader}, deletes the stored position,
 * drops the slot and starts a fresh engine at the current end of the log — gated by an advisory lock so only
 * one instance runs the sequence at a time.
 */
@Component
public class ChangeStreamRecovery {

    private static final Duration ENGINE_STOP_TIMEOUT = Duration.ofSeconds(10);

    /** The table Debezium's {@code JdbcOffsetBackingStore} keeps the engine's stored position in. */
    private static final String OFFSET_STORAGE_TABLE = "debezium_offset_storage";

    private final ChangeStreamReader changeStreamReader;
    private final DataSource dataSource;
    private final CdcProperties properties;
    private final Logger log;

    public ChangeStreamRecovery(
            ChangeStreamReader changeStreamReader,
            DataSource dataSource,
            CdcProperties properties,
            LoggerFactory loggerFactory) {
        this.changeStreamReader = changeStreamReader;
        this.dataSource = dataSource;
        this.properties = properties;
        this.log = loggerFactory.getLogger(ChangeStreamRecovery.class);
    }

    public SlotRecoveryOutcome recover() {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryAdvisoryLock(connection)) {
                return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.LOCK_HELD);
            }

            try {
                return runSequence(connection);
            } finally {
                releaseAdvisoryLock(connection);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reach the database for the recovery sequence", e);
        }
    }

    private SlotRecoveryOutcome runSequence(Connection connection) throws SQLException {
        Optional<SlotSnapshot> slot = readSlot(connection);

        if (slot.isPresent() && slot.get().walStatus() != ReplicationSlotState.LOST) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.SLOT_NOT_LOST);
        }

        Optional<String> abandonedPosition = slot.map(SlotSnapshot::confirmedFlushLsn);

        if (!changeStreamReader.stop(ENGINE_STOP_TIMEOUT)) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.ENGINE_DID_NOT_STOP);
        }

        if (!deleteStoredPosition(connection)) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.POSITION_NOT_DELETED);
        }

        if (slot.isPresent() && !dropSlot(connection)) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.SLOT_NOT_DROPPED);
        }

        abandonedPosition.ifPresent(
                position -> log.error("Abandoned replication position {} at {}", position, Instant.now()));

        String resumedPosition = readCurrentWalLsn(connection);
        changeStreamReader.start();

        return SlotRecoveryOutcome.rebuilt(abandonedPosition, resumedPosition);
    }

    private boolean tryAdvisoryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(hashtext(?))")) {
            statement.setString(1, properties.slotName());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void releaseAdvisoryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(hashtext(?))")) {
            statement.setString(1, properties.slotName());
            statement.execute();
        }
    }

    private Optional<SlotSnapshot> readSlot(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT wal_status, confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name = ?")) {
            statement.setString(1, properties.slotName());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                String confirmedFlushLsn = resultSet.getString("confirmed_flush_lsn");
                ReplicationSlotState state =
                        ReplicationSlotState.fromNullableWalStatus(resultSet.getString("wal_status"));
                return Optional.of(new SlotSnapshot(state, confirmedFlushLsn));
            }
        }
    }

    private boolean deleteStoredPosition(Connection connection) {
        try {
            boolean tableExists;
            try (ResultSet tables = connection.getMetaData().getTables(null, null, OFFSET_STORAGE_TABLE, null)) {
                tableExists = tables.next();
            }
            if (!tableExists) {
                return true;
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + OFFSET_STORAGE_TABLE)) {
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            log.error("Failed to delete the stored replication position", e);
            return false;
        }
    }

    private boolean dropSlot(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_drop_replication_slot(?)")) {
            statement.setString(1, properties.slotName());
            statement.execute();
            return true;
        } catch (SQLException e) {
            log.error("Failed to drop the replication slot", e);
            return false;
        }
    }

    private String readCurrentWalLsn(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_current_wal_lsn()");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private record SlotSnapshot(ReplicationSlotState walStatus, String confirmedFlushLsn) {}
}
