package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidMoneyException;

import java.util.Currency;

public record CurrencyCode(String code) {

    public CurrencyCode {
        if (code == null || code.isBlank()) {
            throw new InvalidMoneyException("Currency code must not be null or blank");
        }

        code = code.toUpperCase();
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidMoneyException("Unrecognized ISO 4217 currency code: " + code);
        }
    }

    public static CurrencyCode of(String code) {
        // named factory over the compact constructor, which does the normalizing and the rejecting
        return new CurrencyCode(code);
    }
}
