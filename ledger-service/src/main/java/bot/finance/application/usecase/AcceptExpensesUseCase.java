package bot.finance.application.usecase;

import bot.finance.application.dto.AcceptExpensesCommand;
import bot.finance.application.dto.ExpenseAcceptance;
import bot.finance.application.port.AcceptExpensesPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.UserRepository;
import java.time.Clock;

public class AcceptExpensesUseCase implements AcceptExpensesPort {

    private final UserRepository userRepository;
    private final ExpenseProposalRepository expenseProposalRepository;
    private final ReportClearingDispatchPort reportClearingDispatchPort;
    private final Clock clock;
    private final Logger log;

    public AcceptExpensesUseCase(
            UserRepository userRepository,
            ExpenseProposalRepository expenseProposalRepository,
            ReportClearingDispatchPort reportClearingDispatchPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.expenseProposalRepository = expenseProposalRepository;
        this.reportClearingDispatchPort = reportClearingDispatchPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(AcceptExpensesUseCase.class);
    }

    @Override
    public ExpenseAcceptance accept(AcceptExpensesCommand command) {
        // resolves the caller to a stored user, moves the command's ids through
        // ExpenseProposalRepository.acceptByIds, counts accepted against the posted ids for missing, dispatches
        // clearing for every distinct message the move answered, and never dispatches when nothing moved. Logs
        // one line at info per acceptance, carrying the resolved user, how many moved and how many named
        // nothing (D17)
        return null;
    }
}
