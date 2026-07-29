package bot.finance.application.usecase;

import bot.finance.application.dto.NewExpense;
import bot.finance.application.port.CreateExpensePort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.model.Expense;
import java.time.Clock;

public class CreateExpenseUseCase implements CreateExpensePort {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final Clock clock;
    private final Logger log;

    public CreateExpenseUseCase(
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
        this.log = loggerFactory.getLogger(CreateExpenseUseCase.class);
    }

    @Override
    public Expense create(NewExpense newExpense) {
        // rejects an absent command; resolves the external id through UserRepository and throws
        // UnknownUserException when nothing is stored under it; builds the Expense against the
        // resolved user's database id and the command's category id, with Instant.now(clock);
        // stores it, logs the creation at info level, and returns what was stored
        return null;
    }
}
