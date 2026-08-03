package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import java.util.UUID;

/** Identifies one handled message, so a stored proposal can be tied back to the turn that produced it. */
public record MessageReference(UUID value) {

    public MessageReference {
        if (value == null) {
            throw new InvalidIncomingMessageException("message reference must be present");
        }
    }

    /** Mints a fresh reference identifying a newly handled message. */
    public static MessageReference newReference() {
        return new MessageReference(UUID.randomUUID());
    }

    /** Parses the canonical text of a UUID, rejecting an absent, blank or unparseable value. */
    public static MessageReference of(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIncomingMessageException("message reference must be present");
        }
        try {
            return new MessageReference(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new InvalidIncomingMessageException("message reference is not a valid UUID: " + value);
        }
    }
}
