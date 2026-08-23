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

    public static Money ofMajorUnits(BigDecimal amount, CurrencyCode currencyCode) {
        if (amount == null) {
            throw new InvalidMoneyException("Amount must not be null");
        }
        if (currencyCode == null) {
            throw new InvalidMoneyException("Currency code must not be null");
        }

        if (!currencyCode.recordsAmounts()) {
            throw new InvalidMoneyException(currencyCode.code() + " is not a currency an amount can be recorded in");
        }

        int fractionDigits = Currency.getInstance(currencyCode.code()).getDefaultFractionDigits();
        BigDecimal scaled;
        try {
            scaled = amount.movePointRight(fractionDigits).setScale(0);
        } catch (ArithmeticException e) {
            throw new InvalidMoneyException(amount + " is more precise than " + currencyCode.code() + ", which has "
                    + fractionDigits + " decimal places");
        }

        long minorUnits;
        try {
            minorUnits = scaled.longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidMoneyException("Amount is too large to record");
        }

        return new Money(minorUnits, currencyCode);
    }
}
