package bot.finance.ai.domain.value;

import java.util.Optional;

public record ExpenseIntent(
        Operation operation, Optional<String> categoryName, Optional<Money> amount, Optional<String> description)
        implements Intent {

    public ExpenseIntent {
        // rejects a null operation and a null Optional in any of categoryName/amount/description; when
        // operation is CREATE, also rejects an empty amount and an empty categoryName — the catch-all
        // guarantees a fit, so a missing category is model non-compliance rather than a legitimate gap —
        // all with InvalidValueException naming the operation and the missing field
    }

}
