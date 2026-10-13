package bot.finance.application.usecase;

import bot.finance.application.dto.DiscardExpensesCommand;
import bot.finance.application.dto.ExpenseDiscard;
import bot.finance.application.port.DiscardExpensesPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.UserRepository;
import java.time.Clock;

public class DiscardExpensesUseCase implements DiscardExpensesPort {

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ReportClearingDispatchPort reportClearingDispatchPort;
    private final Clock clock;
    private final Logger log;

    public DiscardExpensesUseCase(
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            ReportClearingDispatchPort reportClearingDispatchPort,
            Clock clock,
            LoggerFactory loggerFactory) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.reportClearingDispatchPort = reportClearingDispatchPort;
        this.clock = clock;
        this.log = loggerFactory.getLogger(DiscardExpensesUseCase.class);
    }

    @Override
    public ExpenseDiscard discard(DiscardExpensesCommand command) {
        // stub-intent: refuses an absent command, resolves the caller by stored id, removes their pending rows by
        // id at the clock's instant, hands each touched message to the clearing once, logs one info line with both
        // counts and the caller's external id, and answers discarded and missing
        return null;
    }
}
