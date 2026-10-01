/**
 * The consumer of the ledger's change stream: {@code ChangeStreamConsumer} reads {@code ledger.cdc} as group
 * {@code ai-connector} and hands each entry to {@code ChangeStreamEntryHandler}, which reads it through {@code
 * ChangeStreamEntryReader}, offers it and acknowledges it. {@code ledger.change-stream.*} binds the properties
 * they all take.
 */
package bot.finance.ai.adapter.redis;
