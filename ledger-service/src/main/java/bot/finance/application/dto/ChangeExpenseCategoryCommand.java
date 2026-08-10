package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ExpenseStatus;

public record ChangeExpenseCategoryCommand(
        AuthenticatedUserId userId, ExpenseStatus status, long entryId, long categoryId) {

    public ChangeExpenseCategoryCommand {
        // refuses an absent userId, an absent status, an entryId below 1 and a categoryId below 1, each throwing
        // InvalidExpenseCategoryChangeException naming the field and the bound it broke
    }
}
