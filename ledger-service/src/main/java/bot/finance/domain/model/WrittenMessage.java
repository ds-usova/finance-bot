package bot.finance.domain.model;

import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;

public class WrittenMessage extends Entity {

    private final long userId;
    private final String text;
    private final WrittenMessageStatus status;
    private final Instant createdAt;

    private WrittenMessage(Long id, long userId, String text, WrittenMessageStatus status, Instant createdAt) {
        super(id);
        if (userId <= 0) {
            throw new InvalidWrittenMessageException("userId must be positive");
        }
        if (text == null || text.isBlank()) {
            throw new InvalidWrittenMessageException("text must be present");
        }
        if (status == null) {
            throw new InvalidWrittenMessageException("status must be present");
        }
        if (createdAt == null) {
            throw new InvalidWrittenMessageException("createdAt must be present");
        }
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
