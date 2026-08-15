package bot.finance.ai.domain.exception;

public class CallerVerificationUnavailableException extends RuntimeException {

    public CallerVerificationUnavailableException(String message) {
        super(message);
    }

    public CallerVerificationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
