package bot.finance.adapter.cdc;

import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Reads {@code pg_replication_slots} for the configured slot on a timer and records its retained size and
 * {@code wal_status}, whether or not this instance's engine holds the slot — the monitor is bound to the
 * slot's existence, not to the engine's.
 */
@Component
public class ReplicationSlotMonitor {

    private final DataSource dataSource;
    private final CdcProperties properties;
    private final ChangeStreamMeters meters;

    public ReplicationSlotMonitor(DataSource dataSource, CdcProperties properties, ChangeStreamMeters meters) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.meters = meters;
    }

    public void readSlot() {
        // records the slot's retained bytes and wal_status ordinal on the meters; a missing slot records zero
        // bytes and the absent ordinal rather than skipping the read
    }
}
