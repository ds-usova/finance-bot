package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

public record CategoryRef(long id, String name) {

    public CategoryRef {
        if (id <= 0) {
            throw new InvalidValueException("Id must be positive");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("Name must not be null or blank");
        }
    }
}
