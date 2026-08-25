package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.value.AuthenticatedUserId;

public record ChangeExpenseCategoryCommand(AuthenticatedUserId userId, long entryId, long categoryId) {

    public ChangeExpenseCategoryCommand {
        if (userId == null) {
            throw new InvalidExpenseCategoryChangeException("category change has no userId");
        }
        if (entryId < 1) {
            throw new InvalidExpenseCategoryChangeException("category change has an entry id below 1");
        }
        if (categoryId < 1) {
            throw new InvalidExpenseCategoryChangeException("category change has a categoryId below 1");
        }
    }
}
