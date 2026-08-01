package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

import java.util.Optional;

public record ExpenseIntent(
        Operation operation,
        Optional<String> categoryName,
        Optional<Money> amount,
        Optional<String> description,
        Optional<String> parentCategoryName
) implements Intent {

    public ExpenseIntent {
        if (operation == null) {
            throw new InvalidValueException("Operation must not be null");
        }

        if (categoryName == null) {
            throw new InvalidValueException("Category name must not be null; use Optional.empty() when absent");
        }

        if (amount == null) {
            throw new InvalidValueException("Amount must not be null; use Optional.empty() when absent");
        }

        if (description == null) {
            throw new InvalidValueException("Description must not be null; use Optional.empty() when absent");
        }

        if (parentCategoryName == null) {
            throw new InvalidValueException(
                    "Parent category name must not be null; use Optional.empty() when absent");
        }

        if (operation == Operation.CREATE) {
            if (amount.isEmpty()) {
                throw new InvalidValueException("Operation " + operation.name() + " requires an amount");
            }

            if (categoryName.isEmpty()) {
                throw new InvalidValueException("Operation " + operation.name() + " requires a categoryName");
            }

            // TODO: require a non-empty description for a CREATE expense intent (D32) — an entry missing one
            // is unusable and must be rejected here as amount and categoryName already are.
        }
    }

}
