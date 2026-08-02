package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;

public record ExtractIntentsCommand(
        String text, List<KnownCategory> knownCategories, Optional<CurrencyCode> defaultCurrency) {

    public ExtractIntentsCommand {
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Text must not be null or blank");
        }

        if (knownCategories == null || knownCategories.isEmpty()) {
            throw new InvalidValueException("Known categories must not be null or empty");
        }

        if (knownCategories.stream().anyMatch(category -> category == null)) {
            throw new InvalidValueException("Known categories must not contain a null element");
        }

        if (defaultCurrency == null) {
            throw new InvalidValueException("Default currency Optional must not be null");
        }

        knownCategories = List.copyOf(knownCategories);
    }
}
