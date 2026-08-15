package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record IntentExtractionRequest(
        String text,
        List<String> categoryGroupings,
        String catchAllGrouping,
        Optional<CurrencyCode> defaultCurrency,
        long userId,
        IncomingMessageId incomingMessageId,
        LocalDate currentDate) {

    public IntentExtractionRequest {
        if (text == null || text.isBlank()) {
            throw new InvalidExtractionRequestException("Text must not be null or blank");
        }
        if (categoryGroupings == null || categoryGroupings.isEmpty()) {
            throw new InvalidExtractionRequestException("Category groupings must not be null or empty");
        }
        if (categoryGroupings.stream().anyMatch(grouping -> grouping == null || grouping.isBlank())) {
            throw new InvalidExtractionRequestException("Category groupings must not contain null or blank elements");
        }
        if (catchAllGrouping == null || catchAllGrouping.isBlank()) {
            throw new InvalidExtractionRequestException("Catch-all grouping must not be null or blank");
        }
        if (!categoryGroupings.contains(catchAllGrouping)) {
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

        categoryGroupings = List.copyOf(categoryGroupings);
    }
}
