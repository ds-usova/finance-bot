package bot.finance.adapter.persistence;

import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IncomingTurnRepositoryAdapter implements IncomingTurnRepository {

    private static final String USER_FOREIGN_KEY = "incoming_turn_user_id_fkey";

    private final IncomingTurnEntityRepository incomingTurnEntityRepository;

    public IncomingTurnRepositoryAdapter(IncomingTurnEntityRepository incomingTurnEntityRepository) {
        this.incomingTurnEntityRepository = incomingTurnEntityRepository;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public boolean keep(IncomingTurn turn) {
        ColumnLimits.validateTurnText(turn.text());

        try {
            IncomingTurnEntity entity = IncomingTurnEntity.fromDomain(turn);
            return incomingTurnEntityRepository.insertIfAbsent(
                            entity.userId(),
                            entity.incomingMessageId(),
                            entity.text(),
                            entity.conversationId(),
                            entity.inboundMessageId(),
                            entity.keptAt())
                    > 0;
        } catch (RuntimeException e) {
            if (USER_FOREIGN_KEY.equals(ForeignKeyViolations.constraintName(e))) {
                throw new EntityNotFoundException("user", "no user stored for id " + turn.userId());
            }
            throw new PersistenceFailedException(
                    "failed to keep incoming turn " + turn.reference().value(), e);
        }
    }

    @Override
    public Optional<IncomingTurn> findOldestQueuedKeptSince(Instant keptSince) {
        try {
            return incomingTurnEntityRepository
                    .findOldestQueuedKeptSince(micros(keptSince))
                    .map(IncomingTurnEntity::toDomain);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find the oldest queued incoming turn", e);
        }
    }

    @Override
    public void recordAttempt(long turnId, Instant now) {
        try {
            incomingTurnEntityRepository.recordAttempt(turnId, micros(now));
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to record an attempt on incoming turn " + turnId, e);
        }
    }

    @Override
    public boolean markHandedOver(long turnId, Instant now) {
        try {
            return incomingTurnEntityRepository.markHandedOver(turnId, micros(now)) > 0;
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to mark incoming turn " + turnId + " handed over", e);
        }
    }

    @Override
    public Optional<IncomingTurn> findFor(long userId, IncomingMessageId reference) {
        try {
            return incomingTurnEntityRepository
                    .findForUser(userId, reference.value())
                    .map(IncomingTurnEntity::toDomain);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find incoming turn " + reference.value() + " for user " + userId, e);
        }
    }

    @Override
    public boolean delete(long turnId) {
        try {
            return incomingTurnEntityRepository.deleteTurn(turnId) > 0;
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to delete incoming turn " + turnId, e);
        }
    }

    @Override
    public List<IncomingTurn> findHandedOverBefore(Instant cutoff) {
        try {
            return incomingTurnEntityRepository.findHandedOverBefore(micros(cutoff)).stream()
                    .map(IncomingTurnEntity::toDomain)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find handed-over incoming turns", e);
        }
    }

    @Override
    public List<IncomingTurn> findQueuedForSweep(Instant keptBefore, Instant attemptBefore) {
        try {
            return incomingTurnEntityRepository.findQueuedForSweep(micros(keptBefore), micros(attemptBefore)).stream()
                    .map(IncomingTurnEntity::toDomain)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find queued incoming turns for the sweep", e);
        }
    }

    @Override
    public Optional<Instant> oldestQueuedKeptAt() {
        try {
            return incomingTurnEntityRepository.findOldestQueued().map(IncomingTurnEntity::keptAt);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to read the oldest queued kept-at instant", e);
        }
    }

    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }
}
