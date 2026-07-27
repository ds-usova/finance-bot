package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

import java.util.Optional;

public record ExpenseIntent(
        Operation operation,
        Optional<String> categoryName,
        Optional<Money> amount,
        Optional<String> description
) implements Intent {

    public ExpenseIntent {
        if (operation == null) {
            throw new InvalidValueException("Operation must not be null");
        }

        if (categoryName == null || amount == null || description == null) {
            throw new InvalidValueException("Optional fields must not be null");
        }

        if (operation == Operation.CREATE) {
            if (amount.isEmpty()) {
                throw new InvalidValueException("Operation " + operation.name() + " requires an amount");
            }

            if (categoryName.isEmpty()) {
                throw new InvalidValueException("Operation " + operation.name() + " requires a categoryName");
            }
        }
    }

}
