package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;

public interface HandleWrittenMessagePort {

    /**
     * Hands the oldest waiting text over, answering whether the look goes on: false when nothing is waiting, or when
     * the hand-over cannot be made now and the text stays waiting.
     *
     * @throws PersistenceFailedException if recording the attempt, moving the text, or settling a refused hand-over
     *     fails
     */
    boolean handleNext();
}
