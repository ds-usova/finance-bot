package bot.finance.application.usecase;

import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.HandOverIncomingTurnPort;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.UserPreferenceRepository;
import java.time.Clock;
import java.time.Duration;

public class HandOverIncomingTurnUseCase implements HandOverIncomingTurnPort {

    private final IncomingTurnRepository incomingTurnRepository;
    private final GroupingRepository groupingRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final IntentExtractionPort intentExtractionPort;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final MessageDeliveryPort messageDeliveryPort;
    private final ProposalReportRepository proposalReportRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final Duration maxQueueWait;
    private final Logger log;

    public HandOverIncomingTurnUseCase(
            IncomingTurnRepository incomingTurnRepository,
            GroupingRepository groupingRepository,
            UserPreferenceRepository userPreferenceRepository,
            IntentExtractionPort intentExtractionPort,
            ExpenseRepository expenseRepository,
            SpendingQueryRepository spendingQueryRepository,
            MessageDeliveryPort messageDeliveryPort,
            ProposalReportRepository proposalReportRepository,
            TurnMeters turnMeters,
            Clock clock,
            Duration maxQueueWait,
            LoggerFactory loggerFactory) {
        this.incomingTurnRepository = incomingTurnRepository;
        this.groupingRepository = groupingRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.intentExtractionPort = intentExtractionPort;
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.messageDeliveryPort = messageDeliveryPort;
        this.proposalReportRepository = proposalReportRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.maxQueueWait = maxQueueWait;
        this.log = loggerFactory.getLogger(HandOverIncomingTurnUseCase.class);
    }

    @Override
    public boolean handOverNext() {
        // stub-intent: takes the oldest turn queued since within the wait, prepares it — catalogue, catch-all,
        // default currency —, records the attempt and hands it over for the day it was kept. Accepted: stamps it
        // handed over and answers true. Refused: settles it as failed and answers true. No catch-all: deletes it,
        // counting it unreported, and answers true. Nothing queued, a store failure while preparing, or any other
        // hand-over failure: answers false, leaving it queued
        return false;
    }
}
