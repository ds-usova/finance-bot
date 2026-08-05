package bot.finance.domain.model;

import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;

public final class SpendingQuery extends Entity {

    private final long userId;
    private final SpendingPeriod period;
    private final MessageReference messageReference;
    private final Instant createdAt;

    private SpendingQuery(
            Long id, long userId, SpendingPeriod period, MessageReference messageReference, Instant createdAt) {
        super(id);
        // TODO(RU02): refuse a non-positive user id, an absent period, reference or instant, with
        // InvalidSpendingQueryException
        this.userId = userId;
        this.period = period;
        this.messageReference = messageReference;
        this.createdAt = createdAt;
    }

    public static SpendingQuery newQuery(
            long userId, SpendingPeriod period, MessageReference messageReference, Instant now) {
        return new SpendingQuery(null, userId, period, messageReference, now);
    }

    public static SpendingQuery stored(
            long id, long userId, SpendingPeriod period, MessageReference messageReference, Instant createdAt) {
        return new SpendingQuery(id, userId, period, messageReference, createdAt);
    }

    public long userId() {
        return userId;
    }

    public SpendingPeriod period() {
        return period;
    }

    public MessageReference messageReference() {
        return messageReference;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
