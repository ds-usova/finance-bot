package bot.finance.domain.exception;

public class IntentExtractionFailedException extends RuntimeException {

    public IntentExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
