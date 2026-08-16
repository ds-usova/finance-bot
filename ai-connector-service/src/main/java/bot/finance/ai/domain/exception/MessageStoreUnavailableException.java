package bot.finance.ai.domain.exception;

public class MessageStoreUnavailableException extends MessageStoreFailedException {

    public MessageStoreUnavailableException(String message) {
        super(message);
    }

    public MessageStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
