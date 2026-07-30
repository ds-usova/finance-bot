package bot.finance.adapter.persistence;

import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpenseProposalRepositoryAdapter implements ExpenseProposalRepository {

    private static final String CATEGORY_FOREIGN_KEY = "expense_proposal_category_id_fkey";
    private static final String USER_FOREIGN_KEY = "expense_proposal_user_id_fkey";

    private final ExpenseProposalEntityRepository expenseProposalEntityRepository;

    public ExpenseProposalRepositoryAdapter(ExpenseProposalEntityRepository expenseProposalEntityRepository) {
        this.expenseProposalEntityRepository = expenseProposalEntityRepository;
    }

    @Override
    @Transactional
    public ExpenseProposal create(ExpenseProposal proposal) {
        ColumnLimits.validateExpenseProposalText(
                proposal.description(), proposal.merchant().orElse(null));

        ExpenseProposalEntity saved;
        try {
            saved = expenseProposalEntityRepository.save(ExpenseProposalEntity.fromDomain(proposal));
        } catch (RuntimeException e) {
            throw classify(proposal, e);
        }
        return saved.toDomain();
    }

    private static RuntimeException classify(ExpenseProposal proposal, RuntimeException e) {
        return switch (ForeignKeyViolations.constraintName(e)) {
            case CATEGORY_FOREIGN_KEY ->
                new EntityNotFoundException("category", "no category stored for id " + proposal.categoryId());
            case USER_FOREIGN_KEY -> new EntityNotFoundException("user", "no user stored for id " + proposal.userId());
            case null, default ->
                new PersistenceFailedException("failed to store expense proposal for user " + proposal.userId(), e);
        };
    }
}
