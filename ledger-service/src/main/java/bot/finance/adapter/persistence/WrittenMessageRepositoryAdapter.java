package bot.finance.adapter.persistence;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.model.WrittenMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WrittenMessageRepositoryAdapter implements WrittenMessageRepository {

    private static final String UNSETTLED_UNIQUE_INDEX = "ux_written_message_one_unsettled_per_user";
    private static final String USER_FOREIGN_KEY = "written_message_user_id_fkey";

    private final WrittenMessageEntityRepository writtenMessageEntityRepository;

    public WrittenMessageRepositoryAdapter(WrittenMessageEntityRepository writtenMessageEntityRepository) {
        this.writtenMessageEntityRepository = writtenMessageEntityRepository;
    }

    @Override
    public WrittenMessageView store(WrittenMessage message) {
        // stub-intent: check the text's width through ColumnLimits.validateWrittenText before the write, save the
        // entity, answer its view, and classify a failure: the ux_written_message_one_unsettled_per_user
        // violation as WrittenMessageInProgressException, the written_message_user_id_fkey violation as
        // EntityNotFoundException, anything else as PersistenceFailedException.
        return null;
    }

    @Override
    public Optional<WrittenMessageView> findForUser(long userId, long messageId) {
        // stub-intent: run findByIdAndUserId and map the row to its view; a failure is PersistenceFailedException.
        return Optional.empty();
    }

    @Override
    public List<WrittenMessageView> findUnsettledForUser(long userId) {
        // stub-intent: run the findUnsettledForUser statement (WAITING and READING rows of the user, by id) and map
        // each row to its view; a failure is PersistenceFailedException.
        return List.of();
    }

    @Override
    public Optional<WrittenMessageView> claimOldestWaiting(Instant now) {
        // stub-intent: run the claimOldestWaiting statement, which moves the oldest WAITING row to READING
        // skipping rows locked by another reader, with now truncated to microseconds; map the row to its view; a
        // failure is PersistenceFailedException.
        return Optional.empty();
    }

    @Override
    public boolean stampHandOver(long messageId, Instant now) {
        // stub-intent: run the stampHandOver statement with now truncated to microseconds and answer whether a
        // READING row was updated; a failure is PersistenceFailedException.
        return false;
    }

    @Override
    public boolean settle(long messageId, ReportOutcome outcome, Instant now) {
        // stub-intent: run the settle statement with the outcome's name and now truncated to microseconds, and
        // answer whether a READING row was updated; a failure is PersistenceFailedException.
        return false;
    }

    @Override
    public List<WrittenMessageView> findReadingUpdatedBefore(Instant cutoff) {
        // stub-intent: run the findReadingUpdatedBefore statement (READING rows last updated before the cutoff, by
        // id), with the cutoff truncated to microseconds, and map each row to its view; a failure is
        // PersistenceFailedException.
        return List.of();
    }
}
