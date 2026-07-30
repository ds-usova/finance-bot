package bot.finance.application.port;

import bot.finance.application.dto.CreateExpenseCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;

public interface CreateExpensePort {

    /**
     * @throws InvalidExpenseException if the command is invalid
     * @throws EntityNotFoundException if the command's external id names no stored user, or its category id
     *     names no stored category
     * @throws PersistenceFailedException if storing the expense fails
     */
    Expense create(CreateExpenseCommand createExpenseCommand);
}
