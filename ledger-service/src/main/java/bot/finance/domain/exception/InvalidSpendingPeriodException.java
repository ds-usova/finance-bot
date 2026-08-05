package bot.finance.domain.exception;

public class InvalidSpendingPeriodException extends RuntimeException {

    public InvalidSpendingPeriodException(String message) {
        super(message);
    }
}
