package bot.finance.ai.domain.value;

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

    public SpendingRow {}

    public Optional<MessageIdentity> messageIdentity() {
        // pairs userId with incomingMessageId where the row carries one
        return Optional.empty();
    }
}
