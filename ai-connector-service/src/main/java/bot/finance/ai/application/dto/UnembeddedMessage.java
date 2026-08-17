package bot.finance.ai.application.dto;

public record UnembeddedMessage(long messageId, String text) {

    public UnembeddedMessage {
        // TODO: refuse a null or blank text, as InvalidValueException
    }
}
