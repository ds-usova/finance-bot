package bot.finance.ai.domain.value;

public record UnknownIntent(String reason) implements Intent {

    public UnknownIntent {
        // rejects a null or blank reason with InvalidValueException — an unknown result that does not
        // say why is not useful to the caller
    }

}
