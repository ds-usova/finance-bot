package bot.finance.ai.domain.value;

import java.math.BigDecimal;

public record Money(long minorUnits, CurrencyCode currencyCode) {

    public Money {
        // rejects a null currencyCode and a negative minorUnits with InvalidValueException
    }

    public static Money of(String amount, String currencyCode) {
        // parses amount with new BigDecimal(String) — rejecting a null or non-decimal value, a negative
        // value, and a value with more fractional digits than the currency's scale
        // (java.util.Currency#getDefaultFractionDigits, via CurrencyCode.of(currencyCode)) — all as
        // InvalidValueException, then scales the parsed amount into minor units
        return null;
    }

    public BigDecimal amount() {
        // converts minorUnits back to a BigDecimal scaled by currencyCode's fraction digits
        return null;
    }

}
