package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.value.AuthenticatedUserId;

public record ListCategoriesCommand(AuthenticatedUserId userId, String parentCategoryName) {

    public ListCategoriesCommand {
        if (userId == null) {
            throw new InvalidCategoryException("list categories command has no userId");
        }
        if (parentCategoryName == null || parentCategoryName.isBlank()) {
            throw new InvalidCategoryException("list categories command has no parentCategoryName");
        }
    }
}
