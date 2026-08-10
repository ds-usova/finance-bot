package bot.finance.domain.exception;

public class InvalidMoneyException extends InvalidValueException {

    public InvalidMoneyException(String message) {
        super(message);
    }
}
