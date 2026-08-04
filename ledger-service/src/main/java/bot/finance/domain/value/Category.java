package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidCategoryException;

public record Category(String name) {

    public Category {
        if (name == null || name.isBlank()) {
            throw new InvalidCategoryException("category has no name");
        }
    }
}
