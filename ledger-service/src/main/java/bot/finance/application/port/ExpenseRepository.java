package bot.finance.application.port;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.MessageReference;

public interface ExpenseRepository {

    /**
     * @throws InvalidExpenseException if a value violates a column constraint
     * @throws EntityNotFoundException if the category id names no stored category, or the user id names no
     *     stored user
     * @throws PersistenceFailedException if the write fails
     */
    Expense create(Expense expense);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    int countByMessageReference(long userId, MessageReference reference);
}
