package bot.finance.domain.exception;

public class IntentExtractionRefusedException extends RuntimeException {

    public IntentExtractionRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
