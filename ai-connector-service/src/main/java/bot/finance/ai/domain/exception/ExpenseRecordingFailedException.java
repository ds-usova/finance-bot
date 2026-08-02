package bot.finance.ai.domain.exception;

public class ExpenseRecordingFailedException extends RuntimeException {

    public ExpenseRecordingFailedException(String message) {
        super(message);
    }

    public ExpenseRecordingFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
