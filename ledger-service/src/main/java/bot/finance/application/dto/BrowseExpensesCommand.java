package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ExpenseFilter;

public record BrowseExpensesCommand(AuthenticatedUserId userId, ExpenseFilter filter) {

    public BrowseExpensesCommand {
        // TODO: refuse a null userId and a null filter
    }
}
