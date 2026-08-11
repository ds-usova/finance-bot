package bot.finance.adapter.cdc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Reads {@code pg_replication_slots} for the configured slot on a timer and records its retained size and
 * {@code wal_status}, whether or not this instance's engine holds the slot — the monitor is bound to the
 * slot's existence, not to the engine's.
 */
@Component
public class ReplicationSlotMonitor {

    private static final String SELECT_SLOT_SQL =
            """
            SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS retained_bytes, wal_status
            FROM pg_replication_slots
            WHERE slot_name = ?
            """;

    private final DataSource dataSource;
    private final CdcProperties properties;
    private final ChangeStreamMeters meters;

    public ReplicationSlotMonitor(DataSource dataSource, CdcProperties properties, ChangeStreamMeters meters) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.meters = meters;
    }

    public void readSlot() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SLOT_SQL)) {
            statement.setString(1, properties.slotName());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    meters.setSlotRetainedBytes(0);
                    meters.setSlotWalStatus(ReplicationSlotState.ABSENT);
                    return;
                }

                long retainedBytes = resultSet.getLong("retained_bytes");
                String walStatus = resultSet.getString("wal_status");
                // NULL wal_status means the slot has never been classified against the retention bound yet;
                // treated the same as ChangeStreamRecovery's own read of the column.
                ReplicationSlotState state =
                        walStatus == null ? ReplicationSlotState.LOST : ReplicationSlotState.fromWalStatus(walStatus);
                meters.setSlotRetainedBytes(retainedBytes);
                meters.setSlotWalStatus(state);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read the replication slot", e);
        }
    }
}
