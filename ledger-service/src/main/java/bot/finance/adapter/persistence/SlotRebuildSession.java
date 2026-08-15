package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The statements a slot rebuild runs, all on the connection that holds its advisory lock. Valid only until
 * {@link ReplicationCatalogue#underSlotLock} returns, after which the connection belongs to whoever the pool
 * hands it to next - a statement issued then would run on someone else's session, so every method refuses.
 */
public class SlotRebuildSession {

    /** The table Debezium's {@code JdbcOffsetBackingStore} keeps the engine's stored position in. */
    private static final String OFFSET_STORAGE_TABLE = "debezium_offset_storage";

    private final JdbcTemplate pinned;
    private final String slotName;
    private final Logger log;

    private boolean closed;

    SlotRebuildSession(JdbcTemplate pinned, String slotName, LoggerFactory loggerFactory) {
        this.pinned = pinned;
        this.slotName = slotName;
        this.log = loggerFactory.getLogger(SlotRebuildSession.class);
    }

    public Optional<ReplicationSlotPosition> findSlot() {
        refuseWhenClosed();
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

    /**
     * The table is looked up the way the delete below resolves it, through this connection's search path, so the
     * two can never disagree about which one they mean. A table that is not there is nothing to delete.
     */
    public boolean deleteStoredPosition() {
        refuseWhenClosed();
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
        refuseWhenClosed();
        try {
            pinned.queryForObject("SELECT pg_drop_replication_slot(?)", String.class, slotName);
            return true;
        } catch (DataAccessException e) {
            log.error("Failed to drop the replication slot", e);
            return false;
        }
    }

    public String currentWalLsn() {
        refuseWhenClosed();
        try {
            return pinned.queryForObject("SELECT pg_current_wal_lsn()::text", String.class);
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("failed to read the current write-ahead log position", e);
        }
    }

    void close() {
        closed = true;
    }

    private void refuseWhenClosed() {
        if (closed) {
            throw new IllegalStateException(
                    "the slot rebuild session for " + slotName + " outlived the lock it ran under");
        }
    }
}
