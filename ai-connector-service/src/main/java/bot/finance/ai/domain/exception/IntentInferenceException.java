package bot.finance.ai.domain.exception;

public class IntentInferenceException extends RuntimeException {

    public IntentInferenceException(String message) {
        super(message);
    }

    public IntentInferenceException(String message, Throwable cause) {
        super(message, cause);
    }

}
