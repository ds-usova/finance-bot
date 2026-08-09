package bot.finance.domain.exception;

public class InvalidExpenseAcceptanceException extends RuntimeException {

    public InvalidExpenseAcceptanceException(String message) {
        super(message);
    }
}
