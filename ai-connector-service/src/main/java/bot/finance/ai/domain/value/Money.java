package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Pattern;

public record Money(long minorUnits, CurrencyCode currencyCode) {

    // new BigDecimal(String) also accepts scientific notation (e.g. "1e3"), which this type rejects.
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

    public Money {
        if (currencyCode == null) {
            throw new InvalidValueException("Currency code must not be null");
        }
        if (minorUnits < 0) {
            throw new InvalidValueException("Minor units must not be negative");
        }
    }

    public static Money of(String amount, String currencyCode) {
        if (amount == null) {
            throw new InvalidValueException("Amount must not be null");
        }

        CurrencyCode code = CurrencyCode.of(currencyCode);
        if (!PLAIN_DECIMAL.matcher(amount).matches()) {
            throw new InvalidValueException("Amount is not a valid decimal number: " + amount);
        }

        BigDecimal parsed = new BigDecimal(amount);
        if (parsed.signum() < 0) {
            throw new InvalidValueException("Amount must not be negative: " + amount);
        }

        int fractionDigits = Currency.getInstance(code.code()).getDefaultFractionDigits();
        if (parsed.scale() > fractionDigits) {
            throw new InvalidValueException("Amount has more fractional digits than " + code.code() + " allows: "
                    + amount);
        }
        return new Money(parsed.setScale(fractionDigits).unscaledValue().longValueExact(), code);
    }

    public BigDecimal amount() {
        int fractionDigits = Currency.getInstance(currencyCode.code()).getDefaultFractionDigits();
        return BigDecimal.valueOf(minorUnits, fractionDigits);
    }

}
