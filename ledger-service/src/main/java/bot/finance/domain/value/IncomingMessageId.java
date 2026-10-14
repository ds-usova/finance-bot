package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Identifies one handled message by the message that started it, so a stored row names the turn it came from. */
public record IncomingMessageId(String value) {

    public static final int MAX_BYTES = 56;

    public IncomingMessageId {
        if (value == null || value.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message id must be present");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidIncomingMessageException("incoming message id must be at most " + MAX_BYTES + " bytes");
        }
    }

    /** Derives the id of a turn from the conversation it arrived in and the message that started it. */
    public static IncomingMessageId of(String conversationId, String inboundMessageId) {
        return new IncomingMessageId(conversationId + ":" + inboundMessageId);
    }

    /** Derives the id that the entries of a web turn carry from the written message that started it. */
    public static IncomingMessageId ofWrittenMessage(long writtenMessageId) {
        if (writtenMessageId < 1) {
            throw new InvalidIncomingMessageException("written message id must be at least 1");
        }
        return new IncomingMessageId("web:" + writtenMessageId);
    }

    public Optional<Long> writtenMessageId() {
        // stub-intent: the written message's id when the value names one as ofWrittenMessage writes it, `web:`
        // followed by a positive id; empty for any other value
        return Optional.empty();
    }

    /** Parses a stored or transported value, rejecting an absent, blank or over-long one. */
    public static IncomingMessageId of(String value) {
        return new IncomingMessageId(value);
    }
}
