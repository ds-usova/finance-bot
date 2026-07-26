package bot.finance.ai.domain.value;

import java.util.Optional;

public record CategoryIntent(Operation operation, String name, Optional<String> newName) implements Intent {

    public CategoryIntent {
        // rejects a null operation, a null or blank name, and a null newName Optional (absence is
        // Optional.empty(), never null); when operation is UPDATE, also rejects an empty newName — all
        // with InvalidValueException
    }

}
