package bot.finance.application.usecase;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.HandleWrittenMessagePort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.application.port.WrittenMessageRepository;
import java.time.Clock;
import java.time.Duration;

public class HandleWrittenMessageUseCase implements HandleWrittenMessagePort {

    private final WrittenMessageRepository writtenMessageRepository;
    private final GroupingRepository groupingRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final IntentExtractionPort intentExtractionPort;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final Duration maxQueueWait;
    private final Logger log;

    public HandleWrittenMessageUseCase(
            WrittenMessageRepository writtenMessageRepository,
            GroupingRepository groupingRepository,
            UserPreferenceRepository userPreferenceRepository,
            IntentExtractionPort intentExtractionPort,
            ExpenseRepository expenseRepository,
            SpendingQueryRepository spendingQueryRepository,
            TurnMeters turnMeters,
            Clock clock,
            Duration maxQueueWait,
            LoggerFactory loggerFactory) {
        this.writtenMessageRepository = writtenMessageRepository;
        this.groupingRepository = groupingRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.intentExtractionPort = intentExtractionPort;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.maxQueueWait = maxQueueWait;
        this.log = loggerFactory.getLogger(HandleWrittenMessageUseCase.class);
    }

    @Override
    public boolean handleNext() {
        // stub-intent: takes the oldest text waiting since within the wait, prepares it — catalogue, catch-all,
        // default currency —, records the attempt and hands it over for the day it was filed. Accepted: moves it to
        // READING and answers true. Refused: settles it as failed and answers true. No catch-all: settles it FAILED
        // and answers true. Nothing waiting, a store failure while preparing, or any other hand-over failure:
        // answers false, leaving it waiting
        return false;
    }
}
