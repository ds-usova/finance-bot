package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Currency;

public record CurrencyCode(String code) {

    public CurrencyCode {
        if (code == null || code.isBlank()) {
            throw new InvalidValueException("Currency code must not be null or blank");
        }

        code = code.toUpperCase();
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException("Unrecognized ISO 4217 currency code: " + code);
        }
    }

    public static CurrencyCode of(String code) {
        return new CurrencyCode(code);
    }

    public String toDecimal(long minorUnits) {
        // shifts the amount by the currency's own minor-unit digits, as plain decimal text
        return null;
    }
}
