/**
 * Everything fronting Redis: the connection and template wiring, and the writer that XADDs a change event to
 * the capped stream.
 */
package bot.finance.adapter.redis;
