package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
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

    private final JdbcTemplate jdbcTemplate;
    private final LoggerFactory loggerFactory;
    private final Logger log;

    public ReplicationCatalogue(JdbcTemplate jdbcTemplate, LoggerFactory loggerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.loggerFactory = loggerFactory;
        this.log = loggerFactory.getLogger(ReplicationCatalogue.class);
    }

    public Optional<ReplicationSlotRetention> findSlotRetention(String slotName) {
        try {
            return jdbcTemplate
                    .query(
                            SELECT_SLOT_RETENTION_SQL,
                            (resultSet, rowNumber) -> new ReplicationSlotRetention(
                                    resultSet.getLong("retained_bytes"), resultSet.getString("wal_status")),
                            slotName)
                    .stream()
                    .findFirst();
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("failed to read the replication slot " + slotName, e);
        }
    }

    public boolean publicationExists(String publicationName) {
        try {
            return !jdbcTemplate
                    .queryForList("SELECT 1 FROM pg_publication WHERE pubname = ?", Integer.class, publicationName)
                    .isEmpty();
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("failed to read the publication " + publicationName, e);
        }
    }

    /**
     * Runs the sequence while one connection holds the slot's advisory lock, answering empty when another
     * connection already holds it. An advisory lock is session-scoped, so taking it, running the sequence and
     * releasing it on the same connection is what keeps a pooled session from being handed back still holding it.
     */
    public <T> Optional<T> underSlotLock(String slotName, Function<SlotRebuildSession, T> sequence) {
        try {
            return jdbcTemplate.execute((ConnectionCallback<Optional<T>>) connection -> {
                JdbcTemplate pinned = new JdbcTemplate(new SingleConnectionDataSource(connection, true));

                if (!Boolean.TRUE.equals(
                        pinned.queryForObject("SELECT pg_try_advisory_lock(hashtext(?))", Boolean.class, slotName))) {
                    return Optional.empty();
                }

                SlotRebuildSession session = new SlotRebuildSession(pinned, slotName, loggerFactory);
                try {
                    return Optional.of(sequence.apply(session));
                } finally {
                    session.close();
                    release(pinned, slotName);
                }
            });
        } catch (DataAccessException e) {
            throw new PersistenceFailedException("failed to reach the database for the slot rebuild", e);
        }
    }

    /**
     * A release that throws must not replace whatever the sequence raised, so it is logged rather than thrown.
     * {@code pg_advisory_unlock_all} is the second attempt: this connection goes back to the pool either way, and
     * a session that keeps the lock refuses every later rebuild drawing it.
     */
    private void release(JdbcTemplate pinned, String slotName) {
        try {
            pinned.queryForObject("SELECT pg_advisory_unlock(hashtext(?))", Boolean.class, slotName);
        } catch (DataAccessException e) {
            log.error("Failed to release the advisory lock on replication slot {}", slotName, e);
            releaseEverything(pinned, slotName);
        }
    }

    private void releaseEverything(JdbcTemplate pinned, String slotName) {
        try {
            pinned.queryForObject("SELECT pg_advisory_unlock_all()", String.class);
        } catch (DataAccessException e) {
            log.error("The connection for replication slot {} goes back to the pool still locked", slotName, e);
        }
    }
}
