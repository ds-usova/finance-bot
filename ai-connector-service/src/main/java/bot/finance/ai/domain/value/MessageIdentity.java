package bot.finance.ai.domain.value;

public record MessageIdentity(long userId, String incomingMessageId) {

    public static MessageIdentity of(String subject, String incomingMessageId) {
        // parses the token's subject as the internal user id and pairs it with the incoming message id,
        // refusing a blank or non-numeric subject and a blank message id as InvalidValueException
        return null;
    }
}
