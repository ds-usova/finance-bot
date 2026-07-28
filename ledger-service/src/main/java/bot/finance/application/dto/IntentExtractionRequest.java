package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;

public record IntentExtractionRequest(
        String text, List<String> knownCategories, Optional<CurrencyCode> defaultCurrency) {

    public IntentExtractionRequest {
        if (text == null || text.isBlank()) {
            throw new InvalidExtractionRequestException("Text must not be null or blank");
        }
        if (knownCategories == null || knownCategories.isEmpty()) {
            throw new InvalidExtractionRequestException("Known categories must not be null or empty");
        }
        if (knownCategories.stream().anyMatch(category -> category == null || category.isBlank())) {
            throw new InvalidExtractionRequestException("Known categories must not contain a null or blank element");
        }
        if (defaultCurrency == null) {
            throw new InvalidExtractionRequestException(
                    "Default currency must not be null; use Optional.empty() when absent");
        }

        knownCategories = List.copyOf(knownCategories);
    }
}
