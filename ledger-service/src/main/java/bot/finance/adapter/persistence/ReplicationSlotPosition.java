package bot.finance.adapter.persistence;

/** Where a replication slot has confirmed up to, and the state Postgres reports it in. */
public record ReplicationSlotPosition(String walStatus, String confirmedFlushLsn) {}
