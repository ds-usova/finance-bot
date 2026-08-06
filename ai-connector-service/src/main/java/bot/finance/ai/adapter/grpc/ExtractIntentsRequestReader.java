package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Reads a request off the wire into the command the application acts on.
 *
 * <p>Every refusal this RPC makes is stated here, in the order the caller meets them, and every value the
 * request carries as text is read exactly once — the currency code and the day the turn runs on are each parsed
 * where they are checked, so no later reader has to trust that someone else already looked.
 */
final class ExtractIntentsRequestReader {

    private ExtractIntentsRequestReader() {}

    /**
     * @throws InvalidValueException if the request carries something the service cannot act on; the message is
     *     the description the caller is refused with
     */
    static ExtractIntentsCommand toCommand(ExtractIntentsRequest request) {
        if (request.getText().isBlank()) {
            throw new InvalidValueException("Text must not be blank");
        }
        if (request.getCategoryGroupingsList().isEmpty()) {
            throw new InvalidValueException("Category groupings must not be empty");
        }
        if (request.getCategoryGroupingsList().stream().anyMatch(String::isBlank)) {
            throw new InvalidValueException("Category groupings must not contain a blank name");
        }
        if (request.getCatchAllGrouping().isBlank()) {
            throw new InvalidValueException("Catch-all grouping must not be blank");
        }
        if (!request.getCategoryGroupingsList().contains(request.getCatchAllGrouping())) {
            throw new InvalidValueException("Catch-all grouping must be one of the category groupings");
        }

        // The day is read before the currency, so a request carrying both faults is refused for the same one
        // it always was.
        LocalDate currentDate = currentDate(request);
        Optional<CurrencyCode> defaultCurrency = defaultCurrency(request);

        return new ExtractIntentsCommand(
                request.getText(),
                request.getCategoryGroupingsList(),
                request.getCatchAllGrouping(),
                defaultCurrency,
                currentDate);
    }

    private static LocalDate currentDate(ExtractIntentsRequest request) {
        if (request.getCurrentDate().isBlank()) {
            throw new InvalidValueException("Current date must not be blank");
        }
        try {
            return LocalDate.parse(request.getCurrentDate());
        } catch (DateTimeParseException e) {
            throw new InvalidValueException("Current date must be an ISO-8601 date");
        }
    }

    private static Optional<CurrencyCode> defaultCurrency(ExtractIntentsRequest request) {
        if (!request.hasDefaultCurrency()) {
            return Optional.empty();
        }
        try {
            return Optional.of(CurrencyCode.of(request.getDefaultCurrency()));
        } catch (InvalidValueException e) {
            throw new InvalidValueException("Unrecognized ISO 4217 currency code: " + request.getDefaultCurrency());
        }
    }
}
