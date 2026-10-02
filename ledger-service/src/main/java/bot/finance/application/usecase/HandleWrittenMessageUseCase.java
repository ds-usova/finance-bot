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

public class HandleWrittenMessageUseCase implements HandleWrittenMessagePort {

    private final WrittenMessageRepository writtenMessageRepository;
    private final GroupingRepository groupingRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final IntentExtractionPort intentExtractionPort;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
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
            LoggerFactory loggerFactory) {
        this.writtenMessageRepository = writtenMessageRepository;
        this.groupingRepository = groupingRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.intentExtractionPort = intentExtractionPort;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.log = loggerFactory.getLogger(HandleWrittenMessageUseCase.class);
    }

    @Override
    public boolean handleNext() {
        // stub-intent: claim the oldest waiting text, prepare the turn, stamp the hand-over, extract the intents,
        // read the stored result back and settle the text with its outcome, answering whether a text was claimed.
        // Logs a line at the claim and at the settle, naming web:<id> and the person.
        return false;
    }
}
