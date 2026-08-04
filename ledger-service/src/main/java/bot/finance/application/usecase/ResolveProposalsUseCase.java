package bot.finance.application.usecase;

import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ResolveProposalsPort;
import bot.finance.application.port.UserRepository;
import java.time.Clock;

public class ResolveProposalsUseCase implements ResolveProposalsPort {

    private final UserRepository userRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final ExpenseRepository expenseRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final Clock clock;
    private final Logger log;

    public ResolveProposalsUseCase(
            UserRepository userRepository,
            ExpenseProposalRepository expenseProposalRepository,
            ExpenseRepository expenseRepository,
            MessageDeliveryPort messageDeliveryPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.expenseProposalRepository = expenseProposalRepository;
        this.expenseRepository = expenseRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(ResolveProposalsUseCase.class);
    }

    @Override
    public void resolve(ResolveProposalsCommand command) {
        // looks the user up by external id; accepts or discards that user's proposals under the command's
        // reference; when nothing moved, counts the expenses already stored under it; maps the two counts onto a
        // ResolutionOutcome and acknowledges through MessageDeliveryPort
    }
}
