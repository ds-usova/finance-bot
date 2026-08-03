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

    // TODO: reject a null amount; read the currency's getDefaultFractionDigits(); build the minor units as
    // amount.movePointRight(fractionDigits).setScale(0).longValueExact(). Every rejection is an
    // InvalidMoneyException, so none of them reaches the tool as the catch-all - the two ArithmeticExceptions
    // that sequence throws are caught here and rethrown as one:
    //   fraction digits < 0        -> "XAU is not a currency an amount can be recorded in"
    //   setScale(0) drops a digit  -> "12.505 is more precise than EUR, which has 2 decimal places"
    //   longValueExact() overflows -> "Amount is too large to record"
    public static Money ofMajorUnits(BigDecimal amount, CurrencyCode currencyCode) {
        return new Money(0, currencyCode);
    }
}
