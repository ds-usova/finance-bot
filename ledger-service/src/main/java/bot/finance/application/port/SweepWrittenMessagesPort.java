package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;

public interface SweepWrittenMessagesPort {

    /**
     * Settles every text left reading past the turn's longest life, answering how many it settled.
     *
     * @throws PersistenceFailedException if a read or write fails
     */
    int sweep();
}
