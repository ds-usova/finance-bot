package bot.finance.application.usecase;

import bot.finance.application.dto.CreateExpenseCommand;
import bot.finance.application.port.CreateExpensePort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
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
    public Expense create(CreateExpenseCommand command) {
        if (command == null) {
            throw new InvalidExpenseException("new expense command is absent");
        }
        User user = userRepository
                .findByExternalId(command.userExternalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user", "no user stored under external id " + command.userExternalId()));
        Instant now = Instant.now(clock);
        Expense expense = Expense.newExpense(
                user.id().orElseThrow(),
                command.categoryId(),
                command.description(),
                command.merchant(),
                command.money(),
                now);
        Expense created = expenseRepository.create(expense);
        log.info("created expense for user with external id {}", command.userExternalId());
        return created;
    }
}
