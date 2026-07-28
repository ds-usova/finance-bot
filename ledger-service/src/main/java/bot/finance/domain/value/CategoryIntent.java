package bot.finance.domain.value;

import java.util.Optional;

public record CategoryIntent(Operation operation, String name, Optional<String> newName) implements Intent {

    public CategoryIntent {
        // TODO: GU03 rejects a null operation, a null or blank name, a null newName Optional, and an
        // UPDATE with no new name, each with InvalidIntentException.
    }
}
