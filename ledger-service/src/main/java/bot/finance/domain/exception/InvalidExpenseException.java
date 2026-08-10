package bot.finance.domain.exception;

public class InvalidExpenseException extends InvalidValueException {

    public InvalidExpenseException(String message) {
        super(message);
    }
}
