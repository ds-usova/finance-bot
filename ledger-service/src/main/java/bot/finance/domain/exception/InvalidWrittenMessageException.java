package bot.finance.domain.exception;

public class InvalidWrittenMessageException extends InvalidValueException {

    public InvalidWrittenMessageException(String message) {
        super(message);
    }
}
