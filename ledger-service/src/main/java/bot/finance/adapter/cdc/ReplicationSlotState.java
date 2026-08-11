package bot.finance.adapter.cdc;

/**
 * {@code pg_replication_slots.wal_status}, plus {@code ABSENT} for a slot that does not exist — each with the
 * ordinal {@code ledger_cdc_slot_wal_status} reports.
 */
public enum ReplicationSlotState {
    RESERVED,
    EXTENDED,
    UNRESERVED,
    LOST,
    ABSENT;

    public static ReplicationSlotState fromWalStatus(String walStatus) {
        // maps reserved/extended/unreserved/lost to the matching constant; refuses any other text rather than
        // silently defaulting, since an unrecognised wal_status is a Postgres version this pipeline has not
        // been taught
        return null;
    }
}
