package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record IntentExtractionRequest(
        String text,
        List<CatalogueEntry> catalogue,
        String catchAllGrouping,
        Optional<CurrencyCode> defaultCurrency,
        long userId,
        IncomingMessageId incomingMessageId,
        LocalDate currentDate) {

    public IntentExtractionRequest {
        if (text == null || text.isBlank()) {
            throw new InvalidExtractionRequestException("Text must not be null or blank");
        }
        if (catalogue == null || catalogue.isEmpty()) {
            throw new InvalidExtractionRequestException("Category groupings must not be null or empty");
        }
        List<String> groupings = groupingsOf(catalogue);
        if (groupings.stream().anyMatch(grouping -> grouping == null || grouping.isBlank())) {
            throw new InvalidExtractionRequestException("Category groupings must not contain null or blank elements");
        }
        if (catchAllGrouping == null || catchAllGrouping.isBlank()) {
            throw new InvalidExtractionRequestException("Catch-all grouping must not be null or blank");
        }
        if (!groupings.contains(catchAllGrouping)) {
            throw new InvalidExtractionRequestException("Catch-all grouping must be one of the category groupings");
        }
        if (defaultCurrency == null) {
            throw new InvalidExtractionRequestException(
                    "Default currency must not be null; use Optional.empty() when absent");
        }
        if (userId <= 0) {
            throw new InvalidExtractionRequestException("User id must be positive");
        }
        if (incomingMessageId == null) {
            throw new InvalidExtractionRequestException("Incoming message id must not be null");
        }
        if (currentDate == null) {
            throw new InvalidExtractionRequestException("Current date must not be null");
        }

        // TODO copy the catalogue, and check each entry's grouping and categories
    }

    public List<String> categoryGroupings() {
        return groupingsOf(catalogue);
    }

    private static List<String> groupingsOf(List<CatalogueEntry> catalogue) {
        return catalogue.stream()
                .map(entry -> entry == null ? null : entry.grouping())
                .toList();
    }
}
