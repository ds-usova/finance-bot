package bot.finance.domain.exception;

public class MessageDeliveryFailedException extends RuntimeException {

    public MessageDeliveryFailedException(String message) {
        super(message);
    }

    public MessageDeliveryFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
