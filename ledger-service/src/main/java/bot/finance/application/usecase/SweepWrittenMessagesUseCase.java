package bot.finance.application.usecase;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SweepWrittenMessagesPort;
import bot.finance.application.port.WrittenMessageRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class SweepWrittenMessagesUseCase implements SweepWrittenMessagesPort {

    private final WrittenMessageRepository writtenMessageRepository;
    private final ExpenseRepository expenseRepository;
    private final Clock clock;
    private final Duration longestTurnLife;
    private final Logger log;

    public SweepWrittenMessagesUseCase(
            WrittenMessageRepository writtenMessageRepository,
            ExpenseRepository expenseRepository,
            Clock clock,
            Duration longestTurnLife,
            LoggerFactory loggerFactory) {
        this.writtenMessageRepository = writtenMessageRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
        this.longestTurnLife = longestTurnLife;
        this.log = loggerFactory.getLogger(SweepWrittenMessagesUseCase.class);
    }

    @Override
    public int sweep() {
        Instant now = clock.instant();
        int swept = 0;
        for (WrittenMessageView message : writtenMessageRepository.findReadingUpdatedBefore(now.minus(longestTurnLife))) {
            if (settle(message, now)) {
                swept++;
            }
        }
        return swept;
    }

    private boolean settle(WrittenMessageView message, Instant now) {
        boolean stored = !expenseRepository
                .findDaysByMessageReference(message.userId(), message.incomingMessageId())
                .isEmpty();
        ReportOutcome outcome = stored ? ReportOutcome.PARTIAL : ReportOutcome.FAILED;
        if (!writtenMessageRepository.settle(message.id(), outcome, now)) {
            return false;
        }
        log.warn(
                "swept stale written message {} of user {} as {}",
                message.incomingMessageId().value(),
                message.userId(),
                outcome);
        return true;
    }
}
