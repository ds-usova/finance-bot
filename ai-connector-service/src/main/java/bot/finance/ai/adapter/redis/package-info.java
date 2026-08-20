/**
 * The consumer of the ledger's change stream: {@code ChangeStreamConsumer} reads {@code ledger.cdc} as group
 * {@code ai-connector}, {@code ChangeStreamEntryReader} maps each entry, and {@code ledger.change-stream.*} binds
 * the properties both take.
 */
package bot.finance.ai.adapter.redis;
