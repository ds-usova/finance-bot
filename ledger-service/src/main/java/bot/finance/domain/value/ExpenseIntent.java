package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidIntentException;
import java.util.Optional;

public record ExpenseIntent(
        Operation operation, Optional<String> categoryName, Optional<Money> amount, Optional<String> description)
        implements Intent {

    public ExpenseIntent {
        if (operation == null) {
            throw new InvalidIntentException("Operation must not be null");
        }
        if (categoryName == null) {
            throw new InvalidIntentException("Category name must not be null; use Optional.empty() when absent");
        }
        if (amount == null) {
            throw new InvalidIntentException("Amount must not be null; use Optional.empty() when absent");
        }
        if (description == null) {
            throw new InvalidIntentException("Description must not be null; use Optional.empty() when absent");
        }

        if (operation == Operation.CREATE) {
            if (amount.isEmpty()) {
                throw new InvalidIntentException("Operation CREATE requires an amount");
            }
            if (categoryName.isEmpty()) {
                throw new InvalidIntentException("Operation CREATE requires a category name");
            }
        }
    }
}
