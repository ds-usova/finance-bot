package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;

public interface HandOverIncomingTurnPort {

    /**
     * Hands the oldest queued turn over, answering whether the look goes on: false when nothing is queued, or when
     * the hand-over cannot be made now and the turn stays queued.
     *
     * @throws PersistenceFailedException if recording the attempt, stamping the hand-over, or settling a refused
     *     hand-over fails
     */
    boolean handOverNext();
}
