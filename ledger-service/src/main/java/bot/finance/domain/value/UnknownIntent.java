package bot.finance.domain.value;

public record UnknownIntent(String reason) implements Intent {

    public UnknownIntent {
        // TODO: GU05 rejects a null or blank reason with InvalidIntentException.
    }
}
