package bot.finance.application.dto;

import bot.finance.domain.value.CurrencyCode;

import java.util.List;
import java.util.Optional;

public record IntentExtractionRequest(String text, List<String> knownCategories, Optional<CurrencyCode> defaultCurrency) {

    public IntentExtractionRequest {
        // TODO: GU06 rejects a null or blank text, a null or empty knownCategories, a null or blank
        // entry in knownCategories, and a null defaultCurrency Optional, with
        // InvalidExtractionRequestException; keeps knownCategories as an unmodifiable copy.
    }
}
