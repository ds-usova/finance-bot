package bot.finance.ai.domain.exception;

public class ClassificationFailedException extends RuntimeException {

    public ClassificationFailedException(String message) {
        super(message);
    }

    public ClassificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
