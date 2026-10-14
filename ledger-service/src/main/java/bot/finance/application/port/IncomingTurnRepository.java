package bot.finance.application.port;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidIncomingTurnException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncomingTurnRepository {

    /**
     * Keeps the turn, queued, answering false when a turn is already kept under its message.
     *
     * @throws InvalidIncomingTurnException if the text is longer than the column
     * @throws EntityNotFoundException if the user id names no stored user
     * @throws PersistenceFailedException if the write fails
     */
    boolean keep(IncomingTurn turn);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    Optional<IncomingTurn> findOldestQueuedKeptSince(Instant keptSince);

    /**
     * @throws PersistenceFailedException if the write fails
     */
    void recordAttempt(long turnId, Instant now);

    /**
     * Stamps the turn handed over, answering whether it was still kept and queued.
     *
     * @throws PersistenceFailedException if the write fails
     */
    boolean markHandedOver(long turnId, Instant now);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    Optional<IncomingTurn> findFor(long userId, IncomingMessageId reference);

    /**
     * Deletes the turn, answering whether this call removed it.
     *
     * @throws PersistenceFailedException if the write fails
     */
    boolean delete(long turnId);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<IncomingTurn> findHandedOverBefore(Instant cutoff);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<IncomingTurn> findQueuedForSweep(Instant keptBefore, Instant attemptBefore);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    Optional<Instant> oldestQueuedKeptAt();
}
