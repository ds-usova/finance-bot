package bot.finance.application.port;

import bot.finance.application.dto.AcceptExpensesCommand;
import bot.finance.application.dto.ExpenseAcceptance;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface AcceptExpensesPort {

    /**
     * @throws InvalidExpenseAcceptanceException if the command or its ids are absent or malformed
     * @throws EntityNotFoundException if the caller names no stored user
     * @throws PersistenceFailedException if the write fails
     */
    ExpenseAcceptance accept(AcceptExpensesCommand command);
}
