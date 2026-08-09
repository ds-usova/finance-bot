package bot.finance.domain.model;

import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;

public final class SpendingQuery extends Entity {

    private final long userId;
    private final SpendingPeriod period;
    private final IncomingMessageId incomingMessageId;
    private final Instant createdAt;

    private SpendingQuery(
            Long id, long userId, SpendingPeriod period, IncomingMessageId incomingMessageId, Instant createdAt) {
        super(id);
        if (userId <= 0) {
            throw new InvalidSpendingQueryException("user id must be positive");
        }
        if (period == null) {
            throw new InvalidSpendingQueryException("period must be present");
        }
        if (incomingMessageId == null) {
            throw new InvalidSpendingQueryException("incoming message id must be present");
        }
        if (createdAt == null) {
            throw new InvalidSpendingQueryException("created at must be present");
        }
        this.userId = userId;
        this.period = period;
        this.incomingMessageId = incomingMessageId;
        this.createdAt = createdAt;
    }

    public static SpendingQuery newQuery(
            long userId, SpendingPeriod period, IncomingMessageId incomingMessageId, Instant now) {
        return new SpendingQuery(null, userId, period, incomingMessageId, now);
    }

    public static SpendingQuery stored(
            long id, long userId, SpendingPeriod period, IncomingMessageId incomingMessageId, Instant createdAt) {
        return new SpendingQuery(id, userId, period, incomingMessageId, createdAt);
    }

    public long userId() {
        return userId;
    }

    public SpendingPeriod period() {
        return period;
    }

    public IncomingMessageId incomingMessageId() {
        return incomingMessageId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
