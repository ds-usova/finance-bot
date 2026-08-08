package bot.finance.domain.exception;

public class InvalidExpenseFilterException extends RuntimeException {

    public InvalidExpenseFilterException(String message) {
        super(message);
    }
}
