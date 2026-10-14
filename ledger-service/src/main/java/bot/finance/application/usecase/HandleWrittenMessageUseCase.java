package bot.finance.application.usecase;

import bot.finance.application.dto.CatalogueEntry;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.dto.WrittenMessageView;
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
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionRefusedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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
    private final ExtractionTurn extractionTurn;
    private final WrittenTurnOutcome writtenTurnOutcome;

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
        this.extractionTurn = new ExtractionTurn(spendingQueryRepository, log);
        this.writtenTurnOutcome = new WrittenTurnOutcome(
                writtenMessageRepository, expenseRepository, spendingQueryRepository, turnMeters, clock, log);
    }

    @Override
    public boolean handleNext() {
        Optional<WrittenMessageView> waiting = writtenMessageRepository.findOldestWaitingFiledSince(
                clock.instant().minus(maxQueueWait));
        if (waiting.isEmpty()) {
            return false;
        }
        WrittenMessageView message = waiting.get();
        IntentExtractionRequest request;
        try {
            request = prepare(message);
        } catch (CatchAllGroupingMissingException e) {
            log.warn("written message {} has no catch-all grouping: {}", message.id(), e.getMessage());
            writtenTurnOutcome.settle(message, TurnEnding.FAILED);
            return true;
        } catch (PersistenceFailedException e) {
            log.warn("failed to prepare written message {}: {}", message.id(), e.getMessage());
            return false;
        }
        writtenMessageRepository.recordAttempt(message.id(), clock.instant());
        try {
            intentExtractionPort.extract(request);
        } catch (IntentExtractionRefusedException e) {
            log.warn("hand-over of written message {} was refused: {}", message.id(), e.getMessage());
            writtenTurnOutcome.settle(message, TurnEnding.FAILED);
            return true;
        } catch (RuntimeException e) {
            log.warn("hand-over of written message {} failed: {}", message.id(), e.getMessage());
            return false;
        }
        writtenMessageRepository.markHandedOver(message.id(), clock.instant());
        return true;
    }

    private IntentExtractionRequest prepare(WrittenMessageView message) {
        List<CatalogueEntry> catalogue = groupingRepository.findCatalogue(message.userId());
        Optional<CurrencyCode> defaultCurrency = userPreferenceRepository.findDefaultCurrency(message.userId());
        String catchAll = extractionTurn.catchAllAmong(
                catalogue.stream().map(CatalogueEntry::grouping).toList());
        return extractionTurn.request(
                message.text(),
                catalogue,
                catchAll,
                defaultCurrency,
                message.userId(),
                message.incomingMessageId(),
                message.filedAt().atZone(ZoneOffset.UTC).toLocalDate());
    }
}
