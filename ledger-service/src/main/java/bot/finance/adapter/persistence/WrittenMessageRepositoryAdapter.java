package bot.finance.adapter.persistence;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.WrittenMessageRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.WrittenMessageInProgressException;
import bot.finance.domain.model.WrittenMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WrittenMessageRepositoryAdapter implements WrittenMessageRepository {

    private static final String UNSETTLED_UNIQUE_INDEX = "ux_written_message_one_unsettled_per_user";
    private static final String USER_FOREIGN_KEY = "written_message_user_id_fkey";

    private final WrittenMessageEntityRepository writtenMessageEntityRepository;

    public WrittenMessageRepositoryAdapter(WrittenMessageEntityRepository writtenMessageEntityRepository) {
        this.writtenMessageEntityRepository = writtenMessageEntityRepository;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public WrittenMessageView store(WrittenMessage message) {
        ColumnLimits.validateWrittenText(message.text());

        try {
            return writtenMessageEntityRepository
                    .save(truncatedToMicros(WrittenMessageEntity.fromDomain(message)))
                    .toView();
        } catch (RuntimeException e) {
            throw classify(message, e);
        }
    }

    @Override
    public Optional<WrittenMessageView> findForUser(long userId, long messageId) {
        try {
            return writtenMessageEntityRepository
                    .findByIdAndUserId(messageId, userId)
                    .map(WrittenMessageEntity::toView);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find written message " + messageId + " for user " + userId, e);
        }
    }

    @Override
    public List<WrittenMessageView> findUnsettledForUser(long userId) {
        try {
            return writtenMessageEntityRepository.findUnsettledForUser(userId).stream()
                    .map(WrittenMessageEntity::toView)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find unsettled written messages for user " + userId, e);
        }
    }

    @Override
    public Optional<WrittenMessageView> claimOldestWaiting(Instant now) {
        try {
            return writtenMessageEntityRepository
                    .claimOldestWaiting(now.truncatedTo(ChronoUnit.MICROS))
                    .map(WrittenMessageEntity::toView);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to claim the oldest waiting written message", e);
        }
    }

    @Override
    public boolean stampHandOver(long messageId, Instant now) {
        try {
            return writtenMessageEntityRepository.stampHandOver(messageId, now.truncatedTo(ChronoUnit.MICROS)) > 0;
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to stamp the hand-over of written message " + messageId, e);
        }
    }

    @Override
    public boolean settle(long messageId, ReportOutcome outcome, Instant now) {
        try {
            return writtenMessageEntityRepository.settle(messageId, outcome.name(), now.truncatedTo(ChronoUnit.MICROS))
                    > 0;
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to settle written message " + messageId, e);
        }
    }

    @Override
    public List<WrittenMessageView> findReadingUpdatedBefore(Instant cutoff) {
        try {
            return writtenMessageEntityRepository
                    .findReadingUpdatedBefore(cutoff.truncatedTo(ChronoUnit.MICROS))
                    .stream()
                    .map(WrittenMessageEntity::toView)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find stale reading written messages", e);
        }
    }

    private static RuntimeException classify(WrittenMessage message, RuntimeException e) {
        if (UNSETTLED_UNIQUE_INDEX.equals(UniqueViolations.constraintName(e))) {
            return new WrittenMessageInProgressException(
                    "user " + message.userId() + " already has a written message waiting or being read");
        }
        if (USER_FOREIGN_KEY.equals(ForeignKeyViolations.constraintName(e))) {
            return new EntityNotFoundException("user", "no user stored for id " + message.userId());
        }
        return new PersistenceFailedException("failed to store written message for user " + message.userId(), e);
    }

    private static WrittenMessageEntity truncatedToMicros(WrittenMessageEntity mapped) {
        return new WrittenMessageEntity(
                mapped.id(),
                mapped.userId(),
                mapped.text(),
                mapped.status(),
                mapped.outcome(),
                mapped.createdAt().truncatedTo(ChronoUnit.MICROS),
                mapped.updatedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
