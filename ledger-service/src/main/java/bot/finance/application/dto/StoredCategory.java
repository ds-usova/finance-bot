package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidCategoryException;
import java.util.Optional;

public record StoredCategory(long id, String name, Optional<String> parentName) {

    public StoredCategory {
        if (id <= 0) {
            throw new InvalidCategoryException("stored category has a non-positive id");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidCategoryException("stored category has no name");
        }
        if (parentName == null) {
            throw new InvalidCategoryException("stored category has no parentName");
        }
    }
}
