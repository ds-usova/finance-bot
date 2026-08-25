package bot.finance.application.usecase;

import bot.finance.application.dto.ChangeExpenseCategoryCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ChangeExpenseCategoryPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.ExpenseEntryNotFoundException;
import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

public class ChangeExpenseCategoryUseCase implements ChangeExpenseCategoryPort {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final Clock clock;
    private final Logger log;

    public ChangeExpenseCategoryUseCase(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
        this.log = loggerFactory.getLogger(ChangeExpenseCategoryUseCase.class);
    }

    @Override
    public ExpenseEntry change(ChangeExpenseCategoryCommand command) {
        if (command == null) {
            throw new InvalidExpenseCategoryChangeException("category change command is absent");
        }

        User user = userRepository.requireById(command.userId().userId());
        long userId = user.id().orElseThrow();
        if (!categoryRepository.existsOwnedCategory(userId, command.categoryId())) {
            throw new InvalidExpenseCategoryChangeException(
                    "categoryId " + command.categoryId() + " is not admitted for this user");
        }

        Instant now = Instant.now(clock);
        Optional<ExpenseEntry> refiled = expenseRepository.refile(userId, command.entryId(), command.categoryId(), now);
        ExpenseEntry entry =
                refiled.orElseThrow(() -> new ExpenseEntryNotFoundException("no entry of yours carries that id"));

        log.info(
                "changed category for user {} entry {} ({}) to category {}",
                userId,
                entry.id(),
                entry.status(),
                entry.categoryId());
        return entry;
    }
}
