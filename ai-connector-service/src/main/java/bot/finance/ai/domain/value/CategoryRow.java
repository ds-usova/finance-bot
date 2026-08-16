package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;

public record CategoryRow(long id, long userId, Optional<Long> parentId, String name) {

    public CategoryRow {
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("Name must not be null or blank");
        }
        if (parentId == null) {
            throw new InvalidValueException("Parent id must not be null");
        }
    }
}
