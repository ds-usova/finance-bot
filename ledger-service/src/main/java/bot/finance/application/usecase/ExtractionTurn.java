package bot.finance.application.usecase;

import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.UserPreferenceRepository;
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

class ExtractionTurn {

    private final UserPreferenceRepository userPreferenceRepository;
    private final IntentExtractionPort intentExtractionPort;
    private final SpendingQueryRepository spendingQueryRepository;
    private final Clock clock;
    private final Logger log;

    ExtractionTurn(
            UserPreferenceRepository userPreferenceRepository,
            IntentExtractionPort intentExtractionPort,
            SpendingQueryRepository spendingQueryRepository,
            Clock clock,
            Logger log) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.intentExtractionPort = intentExtractionPort;
        this.spendingQueryRepository = spendingQueryRepository;
        this.clock = clock;
        this.log = log;
    }

    Optional<CurrencyCode> readDefaultCurrency(long userId) {
        try {
            return userPreferenceRepository.findDefaultCurrency(userId);
        } catch (PersistenceFailedException e) {
            log.warn("failed to read default currency: {}", "user %d: %s".formatted(userId, e.getMessage()));
            return Optional.empty();
        }
    }

    String catchAllAmong(List<String> categoryGroupings) {
        String designated = Grouping.catchAllName();
        if (!categoryGroupings.contains(designated)) {
            throw new CatchAllGroupingMissingException("no grouping named " + designated + " is stored for this user");
        }
        return designated;
    }

    IntentExtractionRequest request(
            String text,
            List<String> categoryGroupings,
            String catchAll,
            Optional<CurrencyCode> defaultCurrency,
            long userId,
            IncomingMessageId reference) {
        return new IntentExtractionRequest(
                text, categoryGroupings, catchAll, defaultCurrency, userId, reference, LocalDate.now(clock));
    }

    boolean extractionFailed(IntentExtractionRequest request) {
        try {
            intentExtractionPort.extract(request);
            return false;
        } catch (IntentExtractionFailedException e) {
            return true;
        }
    }

    void discardPeriods(long userId, IncomingMessageId reference) {
        try {
            spendingQueryRepository.discard(userId, reference);
        } catch (PersistenceFailedException e) {
            log.warn("failed to discard spending queries for message {}: {}", reference, e.getMessage());
        }
    }
}
