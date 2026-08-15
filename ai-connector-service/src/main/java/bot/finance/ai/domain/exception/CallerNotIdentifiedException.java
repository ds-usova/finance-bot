package bot.finance.ai.domain.exception;

public class CallerNotIdentifiedException extends RuntimeException {

    public CallerNotIdentifiedException(String message) {
        super(message);
    }

    public CallerNotIdentifiedException(String message, Throwable cause) {
        super(message, cause);
    }
}
