package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;

public record SpendingRow(
        long expenseId,
        long userId,
        Optional<String> incomingMessageId,
        String description,
        Optional<String> merchant,
        String amount,
        CurrencyCode currencyCode,
        CategoryRef category,
        Optional<CategoryRef> grouping) {

    public SpendingRow {
        if (expenseId <= 0) {
            throw new InvalidValueException("Expense id must be positive");
        }
        if (userId <= 0) {
            throw new InvalidValueException("User id must be positive");
        }
        if (incomingMessageId == null) {
            throw new InvalidValueException("Incoming message id must not be null");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("Description must not be null or blank");
        }
        if (merchant == null) {
            throw new InvalidValueException("Merchant must not be null");
        }
        if (amount == null || amount.isBlank()) {
            throw new InvalidValueException("Amount must not be null or blank");
        }
        if (currencyCode == null) {
            throw new InvalidValueException("Currency code must not be null");
        }
        if (category == null) {
            throw new InvalidValueException("Category must not be null");
        }
        if (grouping == null) {
            throw new InvalidValueException("Grouping must not be null");
        }
    }

    public Optional<MessageIdentity> messageIdentity() {
        return incomingMessageId.map(id -> new MessageIdentity(userId, id));
    }
}
