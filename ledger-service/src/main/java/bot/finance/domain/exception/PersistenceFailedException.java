package bot.finance.domain.exception;

public class PersistenceFailedException extends RuntimeException {

    public PersistenceFailedException(String message, Throwable cause) {
        super(message, cause);
    }

}
