package bot.finance.domain.exception;

public class InvalidIncomingMessageException extends RuntimeException {

    public InvalidIncomingMessageException(String message) {
        super(message);
    }
}
