package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

public record ExpenseToFile(String description, Optional<String> merchant, String amount, CurrencyCode currency) {

    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d+)?");

    public ExpenseToFile {
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("Description must not be null or blank");
        }
        if (merchant == null) {
            throw new InvalidValueException("Merchant must not be null");
        }
        if (amount == null) {
            throw new InvalidValueException("Amount must not be null");
        }
        if (currency == null) {
            throw new InvalidValueException("Currency must not be null");
        }
        if (!DECIMAL.matcher(amount).matches() || new BigDecimal(amount).signum() <= 0) {
            throw new InvalidValueException("Amount must be a positive decimal: " + amount);
        }
        merchant = merchant.filter(value -> !value.isBlank());
    }
}
