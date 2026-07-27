package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

import java.util.Optional;

public record CategoryIntent(Operation operation, String name, Optional<String> newName) implements Intent {

    public CategoryIntent {
        if (operation == null) {
            throw new InvalidValueException("Operation must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("Category name must not be null or blank");
        }
        if (newName == null) {
            throw new InvalidValueException("New name must not be null; use Optional.empty() when absent");
        }
        if (operation == Operation.UPDATE && newName.isEmpty()) {
            throw new InvalidValueException("New name must be present when operation is UPDATE");
        }
    }

}
