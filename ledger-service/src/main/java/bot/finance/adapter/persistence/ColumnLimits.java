package bot.finance.adapter.persistence;

import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.Category;

import java.util.List;

final class ColumnLimits {

    static final int EXTERNAL_ID = 255;
    static final int CATEGORY_NAME = 100;

    private ColumnLimits() {
    }

    static void validateExternalId(String externalId) {
        if (externalId == null) {
            throw new InvalidUserException("external id is required");
        }
        if (externalId.length() > EXTERNAL_ID) {
            throw new InvalidUserException("external id exceeds " + EXTERNAL_ID + " characters");
        }
    }

    static void validateCategoryNames(List<Category> categories) {
        for (Category group : categories) {
            validateCategoryName(group);
            for (Category child : group.children()) {
                validateCategoryName(child);
            }
        }
    }

    private static void validateCategoryName(Category category) {
        if (category.name().length() > CATEGORY_NAME) {
            throw new InvalidCategoryException(
                    "category name exceeds " + CATEGORY_NAME + " characters: " + category.name());
        }
    }

}
