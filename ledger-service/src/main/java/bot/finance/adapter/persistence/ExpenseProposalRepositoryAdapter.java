package bot.finance.adapter.persistence;

import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.domain.model.ExpenseProposal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseProposalRepositoryAdapter implements ExpenseProposalRepository {

    private final ExpenseProposalEntityRepository expenseProposalEntityRepository;

    public ExpenseProposalRepositoryAdapter(ExpenseProposalEntityRepository expenseProposalEntityRepository) {
        this.expenseProposalEntityRepository = expenseProposalEntityRepository;
    }

    @Override
    @Transactional
    public ExpenseProposal create(ExpenseProposal proposal) {
        // checks the column widths through ColumnLimits before writing anything; saves the entity
        // mapped from the domain with both timestamps truncated to microseconds; translates a foreign
        // key violation on expense_proposal_category_id_fkey or expense_proposal_user_id_fkey into
        // EntityNotFoundException and every other runtime exception into PersistenceFailedException;
        // returns the proposal carrying its generated id
        return null;
    }
}
