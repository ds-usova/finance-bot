package bot.finance.domain.exception;

public class WrittenMessageNotFoundException extends EntityNotFoundException {

    public WrittenMessageNotFoundException(String message) {
        super("written message", message);
    }
}
