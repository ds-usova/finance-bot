package bot.finance.application.usecase;

import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ExpensePage;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.model.User;
import java.util.List;

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
        User user = userRepository
                .findByExternalId(command.userId().externalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user",
                        "no user stored under external id " + command.userId().externalId()));
        long userId = user.id().orElseThrow();

        List<ExpenseEntry> entries = expenseRepository.findPage(userId, command.filter());
        long total = expenseRepository.countMatching(userId, command.filter());

        log.debug(
                "resolved user {} browsing expenses with filter {}, found {} entries",
                userId,
                command.filter(),
                entries.size());

        return new ExpensePage(
                entries, command.filter().limit(), command.filter().offset(), total);
    }
}
