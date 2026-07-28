package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidMoneyException;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(long minorUnits, CurrencyCode currencyCode) {

    public Money {
        if (currencyCode == null) {
            throw new InvalidMoneyException("Currency code must not be null");
        }
        if (minorUnits < 0) {
            throw new InvalidMoneyException("Minor units must not be negative");
        }
    }

    public BigDecimal amount() {
        int fractionDigits = Currency.getInstance(currencyCode.code()).getDefaultFractionDigits();
        return BigDecimal.valueOf(minorUnits, fractionDigits);
    }
}
