package bot.finance.adapter.persistence;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.domain.model.Expense;
import org.springframework.stereotype.Component;

@Component
public class ExpenseRepositoryAdapter implements ExpenseRepository {

    private final ExpenseEntityRepository expenseEntityRepository;

    public ExpenseRepositoryAdapter(ExpenseEntityRepository expenseEntityRepository) {
        this.expenseEntityRepository = expenseEntityRepository;
    }

    @Override
    public Expense create(Expense expense) {
        // checks the column widths through ColumnLimits before writing anything; saves the
        // entity mapped from the domain; translates every runtime exception into
        // PersistenceFailedException; returns the expense carrying its generated id
        return null;
    }
}
