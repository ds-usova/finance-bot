package bot.finance.application.usecase;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.dto.DiscardExpensesCommand;
import bot.finance.application.dto.ExpenseDiscard;
import bot.finance.application.port.DiscardExpensesPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidExpenseDiscardException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

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
        if (command == null) {
            throw new InvalidExpenseDiscardException("discard-expenses command is absent");
        }

        User user = userRepository.requireById(command.userId().userId());
        long userId = user.id().orElseThrow();

        List<IncomingMessageId> touchedMessages =
                expenseRepository.discardByIds(userId, command.ids(), Instant.now(clock));
        int discarded = touchedMessages.size();
        int missing = command.ids().ids().size() - discarded;
        dispatchClearing(userId, touchedMessages);

        log.info("discarded expenses for user {}: {} discarded, {} missing", user.externalId(), discarded, missing);

        return new ExpenseDiscard(discarded, missing);
    }

    private void dispatchClearing(long userId, List<IncomingMessageId> touchedMessages) {
        if (touchedMessages.isEmpty()) {
            return;
        }
        List<IncomingMessageId> distinctMessages =
                touchedMessages.stream().distinct().toList();
        reportClearingDispatchPort.dispatch(new ClearEmptiedReportsCommand(userId, distinctMessages));
    }
}
