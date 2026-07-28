package bot.finance.domain.exception;

public class InvalidIntentException extends RuntimeException {

    public InvalidIntentException(String message) {
        super(message);
    }
}
