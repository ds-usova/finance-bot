package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.ProposedExpense;
import bot.finance.ai.domain.exception.ExpenseProposalFailedException;

public interface ExpenseProposalPort {

    /**
     * @throws ExpenseProposalFailedException if the proposal is refused or the ledger cannot be reached
     */
    void propose(ProposedExpense expense);
}
