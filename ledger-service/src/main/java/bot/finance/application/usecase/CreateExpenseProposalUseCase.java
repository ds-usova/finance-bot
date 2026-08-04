package bot.finance.application.usecase;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.dto.StoredCategory;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class CreateExpenseProposalUseCase implements CreateExpenseProposalPort {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final Clock clock;
    private final Logger log;

    public CreateExpenseProposalUseCase(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ExpenseProposalRepository expenseProposalRepository,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
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
        User user = userRepository
                .findByExternalId(command.userId().externalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user",
                        "no user stored under external id " + command.userId().externalId()));
        long categoryId = resolveCategoryId(user, command);
        Instant now = Instant.now(clock);
        ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                user.id().orElseThrow(),
                categoryId,
                command.description(),
                command.merchant(),
                command.money(),
                command.messageReference(),
                now);
        ExpenseProposal created = expenseProposalRepository.create(proposal);
        log.info(
                "created expense proposal for user with external id {}",
                command.userId().externalId());
        return created;
    }

    private long resolveCategoryId(User user, CreateExpenseProposalCommand command) {
        String categoryName = command.categoryName();
        List<StoredCategory> candidates =
                categoryRepository.findByUserIdAndName(user.id().orElseThrow(), categoryName);
        if (candidates.isEmpty()) {
            throw new InvalidCategoryException("no category named " + categoryName + " is stored for this user");
        }
        candidates = narrowByParentName(candidates, command.parentCategoryName());
        if (candidates.isEmpty()) {
            throw new InvalidCategoryException("no category named " + categoryName + " under parent "
                    + command.parentCategoryName() + " is stored for this user");
        }
        return candidates.get(0).id();
    }

    private List<StoredCategory> narrowByParentName(List<StoredCategory> candidates, String parentCategoryName) {
        return candidates.stream()
                .filter(candidate -> candidate
                        .parentName()
                        .filter(parentCategoryName::equals)
                        .isPresent())
                .toList();
    }
}
