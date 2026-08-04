package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import java.util.List;
import java.util.Optional;

public record IntentExtractionRequest(
        String text,
        List<String> categoryGroupings,
        String catchAllGrouping,
        Optional<CurrencyCode> defaultCurrency,
        String userExternalId,
        MessageReference messageReference) {

    public IntentExtractionRequest {
        if (text == null || text.isBlank()) {
            throw new InvalidExtractionRequestException("Text must not be null or blank");
        }
        // TODO RU02: validate categoryGroupings (non-null, non-empty, no null/blank element) and
        // catchAllGrouping (non-blank, one of categoryGroupings)
        if (defaultCurrency == null) {
            throw new InvalidExtractionRequestException(
                    "Default currency must not be null; use Optional.empty() when absent");
        }
        if (userExternalId == null || userExternalId.isBlank()) {
            throw new InvalidExtractionRequestException("User external id must not be null or blank");
        }
        if (messageReference == null) {
            throw new InvalidExtractionRequestException("Message reference must not be null");
        }

        categoryGroupings = List.copyOf(categoryGroupings);
    }
}
