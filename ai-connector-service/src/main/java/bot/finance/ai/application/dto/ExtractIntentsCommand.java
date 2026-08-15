package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record ExtractIntentsCommand(
        String text,
        List<String> categoryGroupings,
        String catchAllGrouping,
        Optional<CurrencyCode> defaultCurrency,
        LocalDate currentDate,
        Optional<MessageIdentity> messageIdentity) {

    public ExtractIntentsCommand {
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Text must not be null or blank");
        }

        if (categoryGroupings == null || categoryGroupings.isEmpty()) {
            throw new InvalidValueException("Category groupings must not be null or empty");
        }
        if (categoryGroupings.stream().anyMatch(grouping -> grouping == null || grouping.isBlank())) {
            throw new InvalidValueException("Category groupings must not contain a null or blank element");
        }
        if (catchAllGrouping == null || catchAllGrouping.isBlank()) {
            throw new InvalidValueException("Catch-all grouping must not be null or blank");
        }
        if (!categoryGroupings.contains(catchAllGrouping)) {
            throw new InvalidValueException("Catch-all grouping must be one of the category groupings");
        }

        if (defaultCurrency == null) {
            throw new InvalidValueException("Default currency Optional must not be null");
        }
        if (currentDate == null) {
            throw new InvalidValueException("Current date must not be null");
        }
        // TODO: refuse a null messageIdentity Optional as InvalidValueException, the same rule defaultCurrency
        // follows

        categoryGroupings = List.copyOf(categoryGroupings);
    }
}
