package bot.finance.domain.model;

import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;

public class WrittenMessage extends Entity {

    private final long userId;
    private final String text;
    private final WrittenMessageStatus status;
    private final Instant createdAt;

    private WrittenMessage(
            Long id, long userId, String text, WrittenMessageStatus status, Instant createdAt) {
        super(id);
        // stub-intent: refuse a user id of 0 or below, a text that is absent or blank, and an absent status or
        // time, each with InvalidWrittenMessageException naming the offending field. The text's length is the
        // persistence adapter's to check.
        this.userId = userId;
        this.text = text;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static WrittenMessage newMessage(long userId, String text, Instant now) {
        return new WrittenMessage(null, userId, text, WrittenMessageStatus.WAITING, now);
    }

    public static WrittenMessage stored(
            long id, long userId, String text, WrittenMessageStatus status, Instant createdAt) {
        return new WrittenMessage(id, userId, text, status, createdAt);
    }

    public long userId() {
        return userId;
    }

    public String text() {
        return text;
    }

    public WrittenMessageStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
