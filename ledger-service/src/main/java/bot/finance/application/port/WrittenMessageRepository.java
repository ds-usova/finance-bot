package bot.finance.application.port;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.WrittenMessageInProgressException;
import bot.finance.domain.model.WrittenMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WrittenMessageRepository {

    /**
     * @throws InvalidWrittenMessageException if the text is longer than the column
     * @throws WrittenMessageInProgressException if the user already has an unsettled text
     * @throws EntityNotFoundException if the user id names no stored user
     * @throws PersistenceFailedException if the write fails
     */
    WrittenMessageView store(WrittenMessage message);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    Optional<WrittenMessageView> findForUser(long userId, long messageId);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<WrittenMessageView> findUnsettledForUser(long userId);

    /**
     * Moves the oldest WAITING text to READING, answering it. An empty result means nothing waits.
     *
     * @throws PersistenceFailedException if the write fails
     */
    Optional<WrittenMessageView> claimOldestWaiting(Instant now);

    /**
     * Stamps the moment the text is handed over to be read, answering whether the message was still READING.
     *
     * @throws PersistenceFailedException if the write fails
     */
    boolean stampHandOver(long messageId, Instant now);

    /**
     * Settles the text with its outcome, answering whether the message was still READING.
     *
     * @throws PersistenceFailedException if the write fails
     */
    boolean settle(long messageId, ReportOutcome outcome, Instant now);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<WrittenMessageView> findReadingUpdatedBefore(Instant cutoff);
}
