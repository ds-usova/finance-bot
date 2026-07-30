package bot.finance.application.port;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;

public interface ExpenseProposalRepository {

    /**
     * @throws InvalidExpenseProposalException if a value violates a column constraint
     * @throws EntityNotFoundException if the user id or category id names no stored row
     * @throws PersistenceFailedException if the write fails
     */
    ExpenseProposal create(ExpenseProposal proposal);
}
