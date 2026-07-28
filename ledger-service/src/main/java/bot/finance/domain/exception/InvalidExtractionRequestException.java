package bot.finance.domain.exception;

public class InvalidExtractionRequestException extends RuntimeException {

    public InvalidExtractionRequestException(String message) {
        super(message);
    }
}
