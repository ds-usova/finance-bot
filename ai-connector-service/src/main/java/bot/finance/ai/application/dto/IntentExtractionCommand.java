package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.CurrencyCode;

import java.util.List;
import java.util.Optional;

public record IntentExtractionCommand(String text, List<String> knownCategories,
                                       Optional<CurrencyCode> defaultCurrency) {

    public IntentExtractionCommand {
        // rejects null/blank text; rejects a null, empty knownCategories list or one containing a null
        // or blank element; rejects a null defaultCurrency Optional (absence is Optional.empty(), never
        // null); defensively copies knownCategories into an unmodifiable list so neither the caller's
        // mutation of its original list nor an attempt to mutate the command's own list is visible here
    }

}
