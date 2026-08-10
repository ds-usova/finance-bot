package bot.finance.application.usecase;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.model.User;
import java.time.Clock;
import java.time.Instant;

public class CreateExpenseProposalUseCase implements CreateExpenseProposalPort {

    private final UserRepository userRepository;
    private final GroupingRepository groupingRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final Clock clock;
    private final Logger log;

    public CreateExpenseProposalUseCase(
            UserRepository userRepository,
            GroupingRepository groupingRepository,
            CategoryRepository categoryRepository,
            ExpenseProposalRepository expenseProposalRepository,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.groupingRepository = groupingRepository;
        this.categoryRepository = categoryRepository;
        this.expenseProposalRepository = expenseProposalRepository;
        this.clock = clock;
        this.log = loggerFactory.getLogger(CreateExpenseProposalUseCase.class);
    }

    @Override
    public ExpenseProposal create(CreateExpenseProposalCommand command) {
        if (command == null) {
            throw new InvalidExpenseProposalException("new expense proposal command is absent");
        }
        User user = userRepository.requireByExternalId(command.userId().externalId());
        long categoryId = resolveCategoryId(user, command);
        Instant now = Instant.now(clock);
        ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                user.id().orElseThrow(),
                categoryId,
                command.description(),
                command.merchant(),
                command.money(),
                command.incomingMessageId(),
                now);
        ExpenseProposal created = expenseProposalRepository.create(proposal);
        log.info(
                "created expense proposal for user with external id {}",
                command.userId().externalId());
        return created;
    }

    private long resolveCategoryId(User user, CreateExpenseProposalCommand command) {
        long userId = user.id().orElseThrow();
        String groupingName = command.groupingName();
        StoredGrouping grouping = groupingRepository
                .findByUserIdAndName(userId, groupingName)
                .orElseThrow(() ->
                        new InvalidGroupingException("no grouping named " + groupingName + " is stored for this user"));
        String categoryName = command.categoryName();
        return categoryRepository
                .findByGroupingAndName(userId, grouping, categoryName)
                .orElseThrow(() -> new InvalidCategoryException("no category named " + categoryName + " under grouping "
                        + groupingName + " is stored for this user"))
                .id();
    }
}
