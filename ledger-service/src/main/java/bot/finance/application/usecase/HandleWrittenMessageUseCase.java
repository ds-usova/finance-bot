package bot.finance.application.usecase;

import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.ReportOutcome;
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
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class HandleWrittenMessageUseCase implements HandleWrittenMessagePort {

    private final WrittenMessageRepository writtenMessageRepository;
    private final GroupingRepository groupingRepository;
    private final ExpenseRepository expenseRepository;
    private final SpendingQueryRepository spendingQueryRepository;
    private final TurnMeters turnMeters;
    private final Clock clock;
    private final Logger log;
    private final ExtractionTurn extractionTurn;

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
        this.expenseRepository = expenseRepository;
        this.spendingQueryRepository = spendingQueryRepository;
        this.turnMeters = turnMeters;
        this.clock = clock;
        this.log = loggerFactory.getLogger(HandleWrittenMessageUseCase.class);
        this.extractionTurn = new ExtractionTurn(
                userPreferenceRepository, intentExtractionPort, spendingQueryRepository, clock, log);
    }

    @Override
    public boolean handleNext() {
        Optional<WrittenMessageView> claimed = writtenMessageRepository.claimOldestWaiting(Instant.now(clock));
        if (claimed.isEmpty()) {
            return false;
        }
        WrittenMessageView message = claimed.get();
        IncomingMessageId reference = message.incomingMessageId();
        log.info("claimed written message {} of user {}", reference, message.userId());

        Optional<IntentExtractionRequest> request = prepare(message, reference);
        if (request.isEmpty()) {
            settle(message, reference, ReportOutcome.FAILED);
            return true;
        }
        try {
            if (!writtenMessageRepository.stampHandOver(message.id(), Instant.now(clock))) {
                log.info("written message {} was settled before its hand-over", reference);
                return true;
            }
        } catch (PersistenceFailedException e) {
            log.warn("failed to stamp the hand-over of written message {}: {}", reference, e.getMessage());
            settle(message, reference, ReportOutcome.FAILED);
            return true;
        }

        boolean extractionFailed = extractionTurn.extractionFailed(request.get());
        List<LocalDate> days = expenseRepository.findDaysByMessageReference(message.userId(), reference);
        List<SpendingPeriod> periods =
                spendingQueryRepository.findPeriodsByMessageReference(message.userId(), reference);
        ReportOutcome outcome = outcomeFor(extractionFailed, !days.isEmpty(), !periods.isEmpty());
        if (extractionFailed) {
            log.error("intent extraction failed for written message {}, outcome {}", reference, outcome);
        }
        if (!periods.isEmpty()) {
            extractionTurn.discardPeriods(message.userId(), reference);
        }
        settle(message, reference, outcome);
        return true;
    }

    private Optional<IntentExtractionRequest> prepare(WrittenMessageView message, IncomingMessageId reference) {
        try {
            List<String> categoryGroupings = groupingRepository.findNamesWithCategories(message.userId());
            String catchAll = extractionTurn.catchAllAmong(categoryGroupings);
            return Optional.of(extractionTurn.request(
                    message.text(),
                    categoryGroupings,
                    catchAll,
                    extractionTurn.readDefaultCurrency(message.userId()),
                    message.userId(),
                    reference));
        } catch (PersistenceFailedException | CatchAllGroupingMissingException e) {
            log.warn("failed to prepare written message {}: {}", reference, e.getMessage());
            return Optional.empty();
        }
    }

    private void settle(WrittenMessageView message, IncomingMessageId reference, ReportOutcome outcome) {
        if (writtenMessageRepository.settle(message.id(), outcome, Instant.now(clock))) {
            turnMeters.countTurn(outcome);
            log.info("settled written message {} of user {} as {}", reference, message.userId(), outcome);
        }
    }

    private ReportOutcome outcomeFor(boolean extractionFailed, boolean hasDays, boolean hasPeriods) {
        if (extractionFailed) {
            return hasDays ? ReportOutcome.PARTIAL : ReportOutcome.FAILED;
        }
        if (hasDays) {
            return ReportOutcome.RECORDED;
        }
        return hasPeriods ? ReportOutcome.ANSWERED : ReportOutcome.NOTHING_IDENTIFIED;
    }
}
