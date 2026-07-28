package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidIntentException;

import java.util.Optional;

public record CategoryIntent(Operation operation, String name, Optional<String> newName) implements Intent {

    public CategoryIntent {
        if (operation == null) {
            throw new InvalidIntentException("Operation must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidIntentException("Category name must not be null or blank");
        }
        if (newName == null) {
            throw new InvalidIntentException("New name must not be null; use Optional.empty() when absent");
        }
        if (operation == Operation.UPDATE && newName.isEmpty()) {
            throw new InvalidIntentException("Operation UPDATE requires a new name");
        }
    }
}
