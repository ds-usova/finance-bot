package bot.finance.application.usecase;

import bot.finance.application.dto.CatalogueEntry;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.port.Logger;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.domain.exception.CatchAllGroupingMissingException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Grouping;
import bot.finance.domain.value.IncomingMessageId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

class ExtractionTurn {

    private final SpendingQueryRepository spendingQueryRepository;
    private final Logger log;

    ExtractionTurn(SpendingQueryRepository spendingQueryRepository, Logger log) {
        this.spendingQueryRepository = spendingQueryRepository;
        this.log = log;
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
            List<CatalogueEntry> catalogue,
            String catchAll,
            Optional<CurrencyCode> defaultCurrency,
            long userId,
            IncomingMessageId reference,
            LocalDate day) {
        return new IntentExtractionRequest(text, catalogue, catchAll, defaultCurrency, userId, reference, day);
    }

    void discardPeriods(long userId, IncomingMessageId reference) {
        try {
            spendingQueryRepository.discard(userId, reference);
        } catch (PersistenceFailedException e) {
            log.warn("failed to discard spending queries for message {}: {}", reference, e.getMessage());
        }
    }
}
