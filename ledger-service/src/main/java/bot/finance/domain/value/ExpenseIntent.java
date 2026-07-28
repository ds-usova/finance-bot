package bot.finance.domain.value;

import java.util.Optional;

public record ExpenseIntent(
        Operation operation, Optional<String> categoryName, Optional<Money> amount, Optional<String> description)
        implements Intent {

    public ExpenseIntent {
        // TODO: GU04 rejects a null operation and a null categoryName/amount/description Optional, and a
        // CREATE with no amount or no category name, each with InvalidIntentException. READ, UPDATE and
        // DELETE carry no requirement of their own.
    }
}
