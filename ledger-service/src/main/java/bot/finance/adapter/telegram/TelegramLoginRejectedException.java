package bot.finance.adapter.telegram;

public class TelegramLoginRejectedException extends RuntimeException {

    public TelegramLoginRejectedException(String message) {
        super(message);
    }
}
