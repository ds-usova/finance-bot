package bot.finance.domain.exception;

/**
 * Thrown when an incoming message is absent, or carries no conversation id or no text.
 */
public class InvalidIncomingMessageException extends RuntimeException {

    public InvalidIncomingMessageException(String message) {
        super(message);
    }

}
