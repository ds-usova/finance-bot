package bot.finance.adapter.cdc;

/**
 * {@code pg_replication_slots.wal_status}, plus {@code ABSENT} for a slot that does not exist — each with the
 * ordinal {@code ledger_cdc_slot_wal_status} reports.
 */
public enum ReplicationSlotState {
    /** The log this slot still needs is within {@code max_wal_size}. Nothing is at risk. */
    RESERVED,

    /** Past {@code max_wal_size}, but the log is still retained for the slot. Recoverable by catching up. */
    EXTENDED,

    /**
     * The log this slot needs is no longer reserved, and the next checkpoint will remove some of it. A consumer
     * that catches up before then returns the slot to {@code EXTENDED} or {@code RESERVED}; one that does not
     * reaches {@code LOST}. This is the last state anything can be done from.
     */
    UNRESERVED,

    /** Log the slot needed has been removed. Terminal — the slot can never be read again, only replaced. */
    LOST,

    /** No such slot exists. Not a Postgres status: what this service reports when the row is missing. */
    ABSENT;

    /**
     * The same mapping for a column read straight off {@code pg_replication_slots}, where a NULL means Postgres
     * has not classified the slot against the retention bound — the log behind it is no longer guaranteed, which
     * is what {@code LOST} says.
     */
    public static ReplicationSlotState fromNullableWalStatus(String walStatus) {
        return walStatus == null ? LOST : fromWalStatus(walStatus);
    }

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
