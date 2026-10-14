package bot.finance.application.usecase;

import bot.finance.application.dto.CatalogueEntry;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.TurnEnding;
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
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionRefusedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.CurrencyCode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
    private final ExtractionTurn extractionTurn;
    private final IncomingTurnReport incomingTurnReport;

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
        this.extractionTurn = new ExtractionTurn(spendingQueryRepository, log);
        this.incomingTurnReport = new IncomingTurnReport(
                incomingTurnRepository,
                expenseRepository,
                spendingQueryRepository,
                messageDeliveryPort,
                proposalReportRepository,
                turnMeters,
                log);
    }

    @Override
    public boolean handOverNext() {
        Optional<IncomingTurn> queued =
                incomingTurnRepository.findOldestQueuedKeptSince(clock.instant().minus(maxQueueWait));
        if (queued.isEmpty()) {
            return false;
        }
        IncomingTurn turn = queued.get();
        IntentExtractionRequest request;
        try {
            request = prepare(turn);
        } catch (CatchAllGroupingMissingException e) {
            log.warn("incoming turn for message {} has no catch-all grouping: {}", turn.reference(), e.getMessage());
            if (incomingTurnRepository.delete(turn.id().orElseThrow())) {
                turnMeters.countUnreported();
            }
            return true;
        } catch (PersistenceFailedException e) {
            log.warn("failed to prepare incoming turn for message {}: {}", turn.reference(), e.getMessage());
            return false;
        }
        long turnId = turn.id().orElseThrow();
        incomingTurnRepository.recordAttempt(turnId, clock.instant());
        try {
            intentExtractionPort.extract(request);
        } catch (IntentExtractionRefusedException e) {
            log.warn("hand-over of incoming turn for message {} was refused: {}", turn.reference(), e.getMessage());
            incomingTurnReport.settle(turn, TurnEnding.FAILED);
            return true;
        } catch (RuntimeException e) {
            log.warn("hand-over of incoming turn for message {} failed: {}", turn.reference(), e.getMessage());
            return false;
        }
        incomingTurnRepository.markHandedOver(turnId, clock.instant());
        return true;
    }

    private IntentExtractionRequest prepare(IncomingTurn turn) {
        List<CatalogueEntry> catalogue = groupingRepository.findCatalogue(turn.userId());
        Optional<CurrencyCode> defaultCurrency = userPreferenceRepository.findDefaultCurrency(turn.userId());
        String catchAll = extractionTurn.catchAllAmong(
                catalogue.stream().map(CatalogueEntry::grouping).toList());
        return extractionTurn.request(
                turn.text(),
                catalogue,
                catchAll,
                defaultCurrency,
                turn.userId(),
                turn.reference(),
                turn.keptOn(ZoneOffset.UTC));
    }
}
