package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;

public interface SweepIncomingTurnsPort {

    /**
     * Settles every turn whose end never arrived and every turn queued past its wait, answering how many it settled.
     *
     * @throws PersistenceFailedException if a read or a delete fails
     */
    int sweep();
}
