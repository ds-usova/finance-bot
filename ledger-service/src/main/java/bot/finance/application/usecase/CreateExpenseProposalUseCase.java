package bot.finance.application.usecase;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.model.User;
import java.time.Clock;
import java.time.Instant;

public class CreateExpenseProposalUseCase implements CreateExpenseProposalPort {

    private final UserRepository userRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final Clock clock;
    private final Logger log;

    public CreateExpenseProposalUseCase(
            UserRepository userRepository,
            ExpenseProposalRepository expenseProposalRepository,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
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
                .findByExternalId(command.userExternalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user", "no user stored under external id " + command.userExternalId()));
        Instant now = Instant.now(clock);
        ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                user.id().orElseThrow(),
                command.categoryId(),
                command.description(),
                command.merchant(),
                command.money(),
                now);
        ExpenseProposal created = expenseProposalRepository.create(proposal);
        log.info("created expense proposal for user with external id {}", command.userExternalId());
        return created;
    }
}
