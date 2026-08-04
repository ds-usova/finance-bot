package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidCategoryException;

public record StoredCategory(long id, String name) {

    public StoredCategory {
        if (id <= 0) {
            throw new InvalidCategoryException("stored category has a non-positive id");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidCategoryException("stored category has no name");
        }
    }
}
