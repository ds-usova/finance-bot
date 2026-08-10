package bot.finance.domain.exception;

public class InvalidExpenseFilterException extends InvalidValueException {

    public InvalidExpenseFilterException(String message) {
        super(message);
    }
}
