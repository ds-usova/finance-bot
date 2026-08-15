package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

public record MessageIdentity(long userId, String incomingMessageId) {

    public MessageIdentity {
        if (incomingMessageId == null || incomingMessageId.isBlank()) {
            throw new InvalidValueException("Incoming message id must not be null or blank");
        }
    }

    public static MessageIdentity of(String subject, String incomingMessageId) {
        if (subject == null || subject.isBlank()) {
            throw new InvalidValueException("Subject must not be null or blank");
        }

        try {
            return new MessageIdentity(Long.parseLong(subject), incomingMessageId);
        } catch (NumberFormatException e) {
            throw new InvalidValueException("Subject must be a numeric user id: " + subject);
        }
    }
}
