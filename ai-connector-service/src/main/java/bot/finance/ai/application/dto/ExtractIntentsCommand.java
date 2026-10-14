package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ExtractIntentsCommand(
        String text,
        List<String> categoryGroupings,
        String catchAllGrouping,
        Optional<CurrencyCode> defaultCurrency,
        LocalDate currentDate,
        Optional<MessageIdentity> messageIdentity,
        List<CatalogueEntry> catalogue) {

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
        if (messageIdentity == null) {
            throw new InvalidValueException("Message identity Optional must not be null");
        }
        if (catalogue == null) {
            throw new InvalidValueException("Catalogue must not be null");
        }
        if (catalogue.stream().anyMatch(Objects::isNull)) {
            throw new InvalidValueException("Catalogue must not contain a null entry");
        }
        List<String> catalogueGroupings =
                catalogue.stream().map(CatalogueEntry::grouping).toList();
        if (catalogueGroupings.size() != Set.copyOf(catalogueGroupings).size()) {
            throw new InvalidValueException("Catalogue must not name a grouping twice");
        }
        if (!catalogueGroupings.equals(categoryGroupings)) {
            throw new InvalidValueException("Catalogue must hold one entry per category grouping, in order");
        }

        categoryGroupings = List.copyOf(categoryGroupings);
        catalogue = List.copyOf(catalogue);
    }
}
