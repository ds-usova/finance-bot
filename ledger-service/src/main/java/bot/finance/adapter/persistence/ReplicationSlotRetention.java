package bot.finance.adapter.persistence;

/** How much log a replication slot is holding back, and the state Postgres reports it in. */
public record ReplicationSlotRetention(long retainedBytes, String walStatus) {}
