package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;

public record ExampleExpense(
        String description,
        String amount,
        CurrencyCode currency,
        Optional<String> categoryName,
        Optional<String> groupingName,
        ExampleOutcome outcome) {

    public ExampleExpense {
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("Description must not be null or blank");
        }
        if (amount == null || amount.isBlank()) {
            throw new InvalidValueException("Amount must not be null or blank");
        }
        if (currency == null) {
            throw new InvalidValueException("Currency must not be null");
        }
        if (categoryName == null) {
            throw new InvalidValueException("Category name must not be null");
        }
        if (groupingName == null) {
            throw new InvalidValueException("Grouping name must not be null");
        }
        if (outcome == null) {
            throw new InvalidValueException("Outcome must not be null");
        }
    }
}
