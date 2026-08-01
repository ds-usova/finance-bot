package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;

public record IntentExtractionRequest(
        String text,
        List<KnownCategory> knownCategories,
        Optional<CurrencyCode> defaultCurrency,
        String userExternalId) {

    public IntentExtractionRequest {
        if (text == null || text.isBlank()) {
            throw new InvalidExtractionRequestException("Text must not be null or blank");
        }
        if (knownCategories == null || knownCategories.isEmpty()) {
            throw new InvalidExtractionRequestException("Known categories must not be null or empty");
        }
        if (knownCategories.stream().anyMatch(category -> category == null)) {
            throw new InvalidExtractionRequestException("Known categories must not contain a null element");
        }
        if (defaultCurrency == null) {
            throw new InvalidExtractionRequestException(
                    "Default currency must not be null; use Optional.empty() when absent");
        }
        if (userExternalId == null || userExternalId.isBlank()) {
            throw new InvalidExtractionRequestException("User external id must not be null or blank");
        }

        knownCategories = List.copyOf(knownCategories);
    }
}
