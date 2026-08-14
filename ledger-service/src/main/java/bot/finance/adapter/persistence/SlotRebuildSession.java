package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The statements a slot rebuild runs, all on the connection that holds its advisory lock. Valid only for the
 * duration of {@link ReplicationCatalogue#underSlotLock}: the connection returns to the pool when that returns.
 */
public class SlotRebuildSession {

    /** The table Debezium's {@code JdbcOffsetBackingStore} keeps the engine's stored position in. */
    private static final String OFFSET_STORAGE_TABLE = "debezium_offset_storage";

    private final JdbcTemplate pinned;
    private final String slotName;
    private final Logger log;

    SlotRebuildSession(JdbcTemplate pinned, String slotName, Logger log) {
        this.pinned = pinned;
        this.slotName = slotName;
        this.log = log;
    }

    public Optional<ReplicationSlotPosition> findSlot() {
        try {
            return pinned
                    .query(
                            "SELECT wal_status, confirmed_flush_lsn FROM pg_replication_slots WHERE slot_name = ?",
                            (resultSet, rowNumber) -> new ReplicationSlotPosition(
                                    resultSet.getString("wal_status"), resultSet.getString("confirmed_flush_lsn")),
                            slotName)
                    .stream()
                    .findFirst();
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("failed to read the replication slot " + slotName, e);
        }
    }

    public boolean deleteStoredPosition() {
        try {
            if (!Boolean.TRUE.equals(
                    pinned.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, OFFSET_STORAGE_TABLE))) {
                return true;
            }
            pinned.update("DELETE FROM " + OFFSET_STORAGE_TABLE);
            return true;
        } catch (DataAccessException e) {
            log.error("Failed to delete the stored replication position", e);
            return false;
        }
    }

    public boolean dropSlot() {
        try {
            pinned.queryForObject("SELECT pg_drop_replication_slot(?)", String.class, slotName);
            return true;
        } catch (DataAccessException e) {
            log.error("Failed to drop the replication slot", e);
            return false;
        }
    }

    public String currentWalLsn() {
        try {
            return pinned.queryForObject("SELECT pg_current_wal_lsn()::text", String.class);
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("failed to read the current write-ahead log position", e);
        }
    }
}
