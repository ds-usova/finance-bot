package bot.finance.domain.value;

import java.math.BigDecimal;

public record Money(long minorUnits, CurrencyCode currencyCode) {

    public Money {
        // TODO: GU02 rejects a null currencyCode and negative minorUnits, with InvalidMoneyException.
    }

    public BigDecimal amount() {
        // scales the minor units by the currency's default fraction digits
        return BigDecimal.ZERO;
    }
}
