package bot.finance.ai.domain.value;

import java.util.Optional;

public record ExampleExpense(
        String description,
        String amount,
        CurrencyCode currency,
        Optional<String> categoryName,
        Optional<String> groupingName,
        ExampleOutcome outcome) {

    public ExampleExpense {
        // TODO: refuse a null or blank description or amount, a null currency, a null outcome, and a null
        // Optional for either name, as InvalidValueException
    }
}
