package bot.finance.application.port;

import bot.finance.application.dto.NewExpense;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.UnknownUserException;
import bot.finance.domain.model.Expense;

public interface CreateExpensePort {

    /**
     * @throws InvalidExpenseException if the command is invalid
     * @throws UnknownUserException if the command's external id names no stored user
     * @throws PersistenceFailedException if storing the expense fails
     */
    Expense create(NewExpense newExpense);
}
