package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;

public record ListCategoriesCommand(AuthenticatedUserId userId, String parentCategoryName) {

    public ListCategoriesCommand {
        // TODO RU03: validate userId (non-null) and parentCategoryName (non-blank), throwing
        // InvalidCategoryException
    }
}
