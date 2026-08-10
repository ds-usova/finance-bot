package bot.finance.application.usecase;

import bot.finance.application.dto.ChangeExpenseCategoryCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ChangeExpenseCategoryPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import java.time.Clock;

public class ChangeExpenseCategoryUseCase implements ChangeExpenseCategoryPort {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final Clock clock;
    private final Logger log;

    public ChangeExpenseCategoryUseCase(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository,
            ExpenseProposalRepository expenseProposalRepository,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.expenseProposalRepository = expenseProposalRepository;
        this.clock = clock;
        this.log = loggerFactory.getLogger(ChangeExpenseCategoryUseCase.class);
    }

    @Override
    public ExpenseEntry change(ChangeExpenseCategoryCommand command) {
        // resolves the caller through userRepository; refuses a categoryId categoryRepository.existsOwnedCategory
        // does not admit, throwing InvalidExpenseCategoryChangeException naming categoryId; refiles the row in
        // the table the command's status names - expenseRepository for RECORDED, expenseProposalRepository for
        // PENDING - with Instant.now(clock), throwing ExpenseEntryNotFoundException when the refile answers
        // nothing; logs the change at info with the resolved user, the status, the entry id and the category it
        // now carries; and answers the row as it now stands
        return null;
    }
}
