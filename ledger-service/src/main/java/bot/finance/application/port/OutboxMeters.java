package bot.finance.application.port;

/**
 * What the outbox could not record. A dropped fact reaches no consumer and leaves the ledger and whatever reads
 * its stream permanently apart, so it is counted rather than only logged — nothing else in the pipeline can see
 * a write that never became a row.
 */
public interface OutboxMeters {

    void countFactsDropped(String type, long facts);
}
