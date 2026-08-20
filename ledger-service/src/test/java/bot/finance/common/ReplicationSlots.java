package bot.finance.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;
import org.postgresql.PGConnection;
import org.postgresql.PGProperty;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates a replication slot, reads its {@code wal_status} back, holds one the way another consumer would, drops
 * a slot a test class left behind, and burns WAL past the container's {@code max_slot_wal_keep_size} so a
 * checkpoint invalidates a slot - the only way a test reaches a lost one, since nothing in the module can set
 * {@code wal_status} directly.
 *
 * <p>It sits at the root of {@code common} rather than in one of the subpackages: it neither seeds a table's rows
 * nor sends a payload nor owns a container's lifecycle, and a bucket of one is worth less than leaving it where
 * its role is honest - the same reasoning {@link LogCapture} sits here under.
 */
public class ReplicationSlots {

    private static final String ONE_MEGABYTE_OF_WAL =
            "SELECT pg_logical_emit_message(true, 'test', repeat('x', 1000000))";

    private ReplicationSlots() {}

    /**
     * A logical slot on {@code pgoutput}, taken without an engine. The name is inlined rather than bound, since
     * {@code execute} takes no parameters.
     */
    public static void create(JdbcTemplate jdbcTemplate, String slotName) {
        jdbcTemplate.execute("SELECT pg_create_logical_replication_slot('" + slotName + "', 'pgoutput')");
    }

    /** The slot's {@code wal_status}, or empty when no slot of that name exists at all. */
    public static Optional<String> walStatus(JdbcTemplate jdbcTemplate, String slotName) {
        return jdbcTemplate
                .queryForList("SELECT wal_status FROM pg_replication_slots WHERE slot_name = ?", String.class, slotName)
                .stream()
                .findFirst();
    }

    /**
     * One round of WAL emitted, switched onto a fresh segment and checkpointed. A checkpoint only invalidates a
     * slot once it actually recycles segments past the bound, so a caller polls {@code wal_status} between rounds
     * rather than trusting one to be enough.
     */
    public static void burnWal(JdbcTemplate jdbcTemplate, int megabytes) {
        for (int i = 0; i < megabytes; i++) {
            emitOneMegabyteOfWal(jdbcTemplate);
        }
        jdbcTemplate.execute("SELECT pg_switch_wal()");
        jdbcTemplate.execute("CHECKPOINT");
    }

    /** One megabyte of WAL no reader will ever confirm, so a slot's retained log moves off zero. */
    public static void emitOneMegabyteOfWal(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(ONE_MEGABYTE_OF_WAL);
    }

    /**
     * Takes the slot the way a second instance of the service would - a replication connection streaming from
     * it - and keeps it until the handle is closed. While it is held, any other attempt to stream from the slot
     * is refused as active for this connection's PID.
     */
    public static AutoCloseable hold(
            String jdbcUrl, String username, String password, String slotName, String publication) throws SQLException {
        Properties properties = new Properties();
        PGProperty.USER.set(properties, username);
        PGProperty.PASSWORD.set(properties, password);
        PGProperty.ASSUME_MIN_SERVER_VERSION.set(properties, "9.4");
        PGProperty.REPLICATION.set(properties, "database");
        PGProperty.PREFER_QUERY_MODE.set(properties, "simple");
        Connection connection = DriverManager.getConnection(jdbcUrl, properties);
        connection
                .unwrap(PGConnection.class)
                .getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(slotName)
                .withSlotOption("proto_version", 1)
                .withSlotOption("publication_names", publication)
                .start();
        return connection::close;
    }

    /**
     * Drops the named slot when it exists and no connection holds it; does nothing otherwise. The name is
     * inlined rather than bound, since {@code execute} takes no parameters.
     */
    public static void dropIfUnheld(JdbcTemplate jdbcTemplate, String slotName) {
        jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots "
                + "WHERE slot_name = '" + slotName + "' AND NOT active");
    }
}
