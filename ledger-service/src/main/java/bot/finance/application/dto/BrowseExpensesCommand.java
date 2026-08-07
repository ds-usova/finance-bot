package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ExpenseFilter;

public record BrowseExpensesCommand(AuthenticatedUserId userId, ExpenseFilter filter) {

    public BrowseExpensesCommand {
        if (userId == null) {
            throw new InvalidUserException("browse expenses command has no user id");
        }
        if (filter == null) {
            throw new InvalidExpenseFilterException("browse expenses command has no filter");
        }
    }
}
