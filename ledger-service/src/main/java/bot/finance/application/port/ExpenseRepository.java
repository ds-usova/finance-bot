package bot.finance.application.port;

import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;

public interface ExpenseRepository {

    /**
     * @throws InvalidExpenseException if a value violates a column constraint
     * @throws PersistenceFailedException if the write fails
     */
    Expense create(Expense expense);
}
