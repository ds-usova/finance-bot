package bot.finance.domain.exception;

public class WrittenMessageInProgressException extends RuntimeException {

    public WrittenMessageInProgressException(String message) {
        super(message);
    }
}
