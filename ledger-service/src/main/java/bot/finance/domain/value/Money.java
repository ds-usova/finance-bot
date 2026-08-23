package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidMoneyException;
import java.math.BigDecimal;

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
        return BigDecimal.valueOf(minorUnits, currencyCode.fractionDigits());
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

        int fractionDigits = currencyCode.fractionDigits();
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
