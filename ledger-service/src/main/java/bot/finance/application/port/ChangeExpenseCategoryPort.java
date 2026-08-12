package bot.finance.application.port;

import bot.finance.application.dto.ChangeExpenseCategoryCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.ExpenseEntryNotFoundException;
import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface ChangeExpenseCategoryPort {

    /**
     * @throws InvalidExpenseCategoryChangeException if the command is absent or malformed, or if categoryId names
     *     no category of the caller's
     * @throws ExpenseEntryNotFoundException if no entry of the caller's carries that id under that status
     * @throws EntityNotFoundException if the caller names no stored user
     * @throws PersistenceFailedException if a read or a write fails
     */
    ExpenseEntry change(ChangeExpenseCategoryCommand command);
}
