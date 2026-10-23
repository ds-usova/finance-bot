package bot.finance.application.port;

import bot.finance.application.dto.DiscardExpensesCommand;
import bot.finance.application.dto.ExpenseDiscard;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseDiscardException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface DiscardExpensesPort {

    /**
     * @throws InvalidExpenseDiscardException if the command is absent
     * @throws EntityNotFoundException if the caller names no stored user
     * @throws PersistenceFailedException if the write fails
     */
    ExpenseDiscard discard(DiscardExpensesCommand command);
}
