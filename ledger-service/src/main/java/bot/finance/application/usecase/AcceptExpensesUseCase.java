package bot.finance.application.usecase;

import bot.finance.application.dto.AcceptExpensesCommand;
import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.dto.ExpenseAcceptance;
import bot.finance.application.port.AcceptExpensesPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseAcceptanceException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

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
        if (command == null) {
            throw new InvalidExpenseAcceptanceException("accept-expenses command is absent");
        }

        User user = userRepository
                .findByExternalId(command.userId().externalId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "user",
                        "no user stored under external id " + command.userId().externalId()));
        long userId = user.id().orElseThrow();

        List<IncomingMessageId> movedMessages =
                expenseProposalRepository.acceptByIds(userId, command.ids(), Instant.now(clock));
        int accepted = movedMessages.size();
        int missing = command.ids().ids().size() - accepted;
        dispatchClearing(userId, movedMessages);

        log.info("accepted expenses for user {}: {} accepted, {} missing", user.externalId(), accepted, missing);

        return new ExpenseAcceptance(accepted, missing);
    }

    private void dispatchClearing(long userId, List<IncomingMessageId> movedMessages) {
        if (movedMessages.isEmpty()) {
            return;
        }
        List<IncomingMessageId> distinctMessages =
                movedMessages.stream().distinct().toList();
        reportClearingDispatchPort.dispatch(new ClearEmptiedReportsCommand(userId, distinctMessages));
    }
}
