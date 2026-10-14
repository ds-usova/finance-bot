package bot.finance.ai.domain.exception;

public class LedgerCallRefusedException extends RuntimeException {

    public LedgerCallRefusedException(String message) {
        super(message);
    }

    public LedgerCallRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
