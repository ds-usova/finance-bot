package bot.finance.application.port;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;

public interface CreateExpenseProposalPort {

    /**
     * @throws InvalidExpenseException if the command is invalid
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws InvalidCategoryException if the command's category name is unknown, a grouping, or ambiguous
     * @throws PersistenceFailedException if storing the proposal fails
     */
    Expense create(CreateExpenseProposalCommand command);
}
