/**
 * Reads the ledger's own write-ahead log through an embedded Debezium engine and republishes each captured
 * row change. Everything fronting that replication feed lives here — the reader, the publisher, the category
 * cache, the meters, the health and recovery surfaces — while the Redis write itself lives in
 * {@code adapter.redis} and the plain row reads it depends on live in {@code adapter.persistence}.
 */
package bot.finance.adapter.cdc;
