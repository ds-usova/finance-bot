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
        return switch (walStatus) {
            case "reserved" -> RESERVED;
            case "extended" -> EXTENDED;
            case "unreserved" -> UNRESERVED;
            case "lost" -> LOST;
            default -> throw new IllegalArgumentException("Undocumented wal_status: " + walStatus);
        };
    }
}
