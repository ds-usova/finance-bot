package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.domain.exception.PersistenceFailedException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * The statements a slot rebuild runs, all on the connection that holds its advisory lock. Valid only for the
 * duration of {@link ReplicationCatalogue#underSlotLock}: the connection returns to the pool when that returns.
 */
public class SlotRebuildSession {

    /** The table Debezium's {@code JdbcOffsetBackingStore} keeps the engine's stored position in. */
    private static final String OFFSET_STORAGE_TABLE = "debezium_offset_storage";

    private final Connection connection;
    private final String slotName;
    private final Logger log;

    SlotRebuildSession(Connection connection, String slotName, Logger log) {
        this.connection = connection;
        this.slotName = slotName;
        this.log = log;
    }

    public Optional<ReplicationSlotPosition> findSlot() {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT wal_status, confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name = ?")) {
            statement.setString(1, slotName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ReplicationSlotPosition(
                        resultSet.getString("wal_status"), resultSet.getString("confirmed_flush_lsn")));
            }
        } catch (SQLException e) {
            throw new PersistenceFailedException("failed to read the replication slot " + slotName, e);
        }
    }

    public boolean deleteStoredPosition() {
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

    public boolean dropSlot() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_drop_replication_slot(?)")) {
            statement.setString(1, slotName);
            statement.execute();
            return true;
        } catch (SQLException e) {
            log.error("Failed to drop the replication slot", e);
            return false;
        }
    }

    public String currentWalLsn() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_current_wal_lsn()");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getString(1);
        } catch (SQLException e) {
            throw new PersistenceFailedException("failed to read the current write-ahead log position", e);
        }
    }
}
