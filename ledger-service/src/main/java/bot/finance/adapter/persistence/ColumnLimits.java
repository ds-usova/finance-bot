package bot.finance.adapter.persistence;

import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.Category;
import bot.finance.domain.value.Grouping;
import java.util.List;

final class ColumnLimits {

    static final int EXTERNAL_ID = 255;
    static final int CATEGORY_NAME = 100;
    static final int DESCRIPTION = 500;
    static final int MERCHANT = 255;
    static final int CURRENCY_CODE = 3;

    private ColumnLimits() {}

    static void validateExternalId(String externalId) {
        if (externalId.length() > EXTERNAL_ID) {
            throw new InvalidUserException("external id exceeds " + EXTERNAL_ID + " characters");
        }
    }

    static void validateCatalogueNames(List<Grouping> groupings) {
        for (Grouping grouping : groupings) {
            validateGroupingName(grouping);
            for (Category category : grouping.categories()) {
                validateCategoryName(category);
            }
        }
    }

    static void validateExpenseText(String description, String merchant) {
        if (description.length() > DESCRIPTION) {
            throw new InvalidExpenseException("description exceeds " + DESCRIPTION + " characters");
        }
        if (merchant != null && merchant.length() > MERCHANT) {
            throw new InvalidExpenseException("merchant exceeds " + MERCHANT + " characters");
        }
    }

    private static void validateCategoryName(Category category) {
        if (category.name().length() > CATEGORY_NAME) {
            throw new InvalidCategoryException(
                    "category name exceeds " + CATEGORY_NAME + " characters: " + category.name());
        }
    }

    private static void validateGroupingName(Grouping grouping) {
        if (grouping.name().length() > CATEGORY_NAME) {
            throw new InvalidGroupingException(
                    "grouping name exceeds " + CATEGORY_NAME + " characters: " + grouping.name());
        }
    }
}
