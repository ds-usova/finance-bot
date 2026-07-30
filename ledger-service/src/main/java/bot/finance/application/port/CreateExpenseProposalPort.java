package bot.finance.application.port;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;

public interface CreateExpenseProposalPort {

    /**
     * @throws InvalidExpenseProposalException if the command is invalid
     * @throws EntityNotFoundException if the command's external id names no stored user, or its category id
     *     names no stored category
     * @throws PersistenceFailedException if storing the proposal fails
     */
    ExpenseProposal create(CreateExpenseProposalCommand command);
}
