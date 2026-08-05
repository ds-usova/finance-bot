package bot.finance.domain.exception;

public class InvalidSpendingQueryException extends RuntimeException {

    public InvalidSpendingQueryException(String message) {
        super(message);
    }
}
