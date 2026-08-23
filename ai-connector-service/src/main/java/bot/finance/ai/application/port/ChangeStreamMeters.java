package bot.finance.ai.application.port;

/** Records outcomes of applying the ledger's change-stream deliveries. */
public interface ChangeStreamMeters {

    void countDropped();
}
