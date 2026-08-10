package bot.finance.domain.exception;

public class InvalidExtractionRequestException extends InvalidValueException {

    public InvalidExtractionRequestException(String message) {
        super(message);
    }
}
