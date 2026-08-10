package bot.finance.domain.exception;

public class InvalidIncomingMessageException extends InvalidValueException {

    public InvalidIncomingMessageException(String message) {
        super(message);
    }
}
