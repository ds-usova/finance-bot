package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;

public record SpendingRow(
        long id,
        long userId,
        Optional<String> incomingMessageId,
        String description,
        Optional<String> merchant,
        long amountMinorUnits,
        CurrencyCode currencyCode,
        long categoryId,
        Optional<String> categoryName,
        Optional<String> groupingName) {

    public SpendingRow {
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("Description must not be null or blank");
        }
        if (currencyCode == null) {
            throw new InvalidValueException("Currency code must not be null");
        }
        if (merchant == null) {
            throw new InvalidValueException("Merchant must not be null");
        }
        if (incomingMessageId == null) {
            throw new InvalidValueException("Incoming message id must not be null");
        }
        if (categoryName == null) {
            throw new InvalidValueException("Category name must not be null");
        }
        if (groupingName == null) {
            throw new InvalidValueException("Grouping name must not be null");
        }
    }

    public Optional<MessageIdentity> messageIdentity() {
        return incomingMessageId.map(id -> new MessageIdentity(userId, id));
    }
}
