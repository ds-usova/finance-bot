package bot.finance.application.usecase;

import bot.finance.application.dto.NewExpense;
import bot.finance.application.port.CreateExpensePort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.UnknownUserException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.model.User;
import java.time.Clock;
import java.time.Instant;

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
        if (newExpense == null) {
            throw new InvalidExpenseException("new expense command is absent");
        }
        User user = userRepository
                .findByExternalId(newExpense.userExternalId())
                .orElseThrow(() ->
                        new UnknownUserException("no user stored under external id " + newExpense.userExternalId()));
        Instant now = Instant.now(clock);
        Expense newExpenseEntity = Expense.newExpense(
                user.id().orElseThrow(),
                newExpense.categoryId(),
                newExpense.description(),
                newExpense.merchant(),
                newExpense.money(),
                now);
        Expense created = expenseRepository.create(newExpenseEntity);
        log.info("created expense for user with external id {}", newExpense.userExternalId());
        return created;
    }
}
