package bot.finance.ai.domain.exception;

public class MessageEmbeddingFailedException extends RuntimeException {

    public MessageEmbeddingFailedException(String message) {
        super(message);
    }

    public MessageEmbeddingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
