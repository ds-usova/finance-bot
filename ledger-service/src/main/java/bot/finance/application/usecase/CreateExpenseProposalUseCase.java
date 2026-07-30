package bot.finance.application.usecase;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.model.ExpenseProposal;
import java.time.Clock;

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
        // rejects an absent command with InvalidExpenseProposalException; resolves the external id
        // through UserRepository and throws EntityNotFoundException("user", ...) when nothing is
        // stored under it; builds the ExpenseProposal against the resolved user's database id and the
        // command's category id, with Instant.now(clock); stores it, logs the creation at info level,
        // and returns what was stored
        return null;
    }
}
