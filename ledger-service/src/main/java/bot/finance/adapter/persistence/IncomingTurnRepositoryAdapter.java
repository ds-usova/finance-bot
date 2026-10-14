package bot.finance.adapter.persistence;

import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IncomingTurnRepositoryAdapter implements IncomingTurnRepository {

    private final IncomingTurnEntityRepository incomingTurnEntityRepository;

    public IncomingTurnRepositoryAdapter(IncomingTurnEntityRepository incomingTurnEntityRepository) {
        this.incomingTurnEntityRepository = incomingTurnEntityRepository;
    }

    @Override
    public boolean keep(IncomingTurn turn) {
        // stub-intent: refuses a text over 4096 UTF-16 units, stores the turn, answers false on the unique index of
        // the message reference, refuses an unknown user as not found, and wraps any other failure
        return false;
    }

    @Override
    public Optional<IncomingTurn> findOldestQueuedKeptSince(Instant keptSince) {
        // stub-intent: the queued turn with the lowest id kept at or after the instant, with its fields; empty when
        // none is queued
        return Optional.empty();
    }

    @Override
    public void recordAttempt(long turnId, Instant now) {
        // stub-intent: moves the turn's last attempt to the instant and leaves it queued
    }

    @Override
    public boolean markHandedOver(long turnId, Instant now) {
        // stub-intent: stamps a queued turn handed over at the instant, answering false for a turn already handed
        // over or deleted
        return false;
    }

    @Override
    public Optional<IncomingTurn> findFor(long userId, IncomingMessageId reference) {
        // stub-intent: the turn kept for the person under the reference; empty when it is kept for another person
        // or not at all
        return Optional.empty();
    }

    @Override
    public boolean delete(long turnId) {
        // stub-intent: deletes the row, answering whether one was removed
        return false;
    }

    @Override
    public List<IncomingTurn> findHandedOverBefore(Instant cutoff) {
        // stub-intent: every turn handed over before the cutoff; a queued turn is never answered
        return List.of();
    }

    @Override
    public List<IncomingTurn> findQueuedForSweep(Instant keptBefore, Instant attemptBefore) {
        // stub-intent: every queued turn kept before the first instant whose latest attempt, or kept-at where it
        // has none, is before the second
        return List.of();
    }

    @Override
    public Optional<Instant> oldestQueuedKeptAt() {
        // stub-intent: the earliest kept-at among queued turns; empty when none is queued
        return Optional.empty();
    }
}
