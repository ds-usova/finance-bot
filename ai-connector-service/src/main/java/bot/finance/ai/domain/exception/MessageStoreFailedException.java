package bot.finance.ai.domain.exception;

public class MessageStoreFailedException extends RuntimeException {

    public MessageStoreFailedException(String message) {
        super(message);
    }

    public MessageStoreFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
