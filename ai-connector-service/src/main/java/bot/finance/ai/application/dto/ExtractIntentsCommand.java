package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;

public record ExtractIntentsCommand(
        String text, List<String> categoryGroupings, String catchAllGrouping, Optional<CurrencyCode> defaultCurrency) {

    public ExtractIntentsCommand {
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Text must not be null or blank");
        }

        // TODO RU08: validate categoryGroupings (non-null, non-empty, no null/blank element) and
        // catchAllGrouping (non-blank, one of categoryGroupings)

        if (defaultCurrency == null) {
            throw new InvalidValueException("Default currency Optional must not be null");
        }

        categoryGroupings = List.copyOf(categoryGroupings);
    }
}
