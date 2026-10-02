package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;

public interface HandleWrittenMessagePort {

    /**
     * Reads the oldest waiting text, answering whether one was claimed.
     *
     * @throws PersistenceFailedException if a read or write fails
     */
    boolean handleNext();
}
