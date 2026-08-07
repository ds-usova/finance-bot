package bot.finance.application.usecase;

import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ExpensePage;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;

public class BrowseExpensesUseCase implements BrowseExpensesPort {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final Logger log;

    public BrowseExpensesUseCase(
            UserRepository userRepository, ExpenseRepository expenseRepository, LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.log = loggerFactory.getLogger(BrowseExpensesUseCase.class);
    }

    @Override
    public ExpensePage browse(BrowseExpensesCommand command) {
        // resolves the caller's user row by external id, reads the filtered page and the matching total
        // through ExpenseRepository, logs the resolved id, the filter and the entry count at DEBUG
        return null;
    }
}
