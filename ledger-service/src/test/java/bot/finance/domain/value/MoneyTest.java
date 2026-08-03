package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidMoneyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @Nested
    @DisplayName("constructing money")
    class MoneyConstructor {

        @Test
        @DisplayName("when the currency code is null - then throws InvalidMoneyException")
        void whenCurrencyCodeIsNull_thenThrowsInvalidMoneyException() {
            assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName("when minor units is -1 - then throws InvalidMoneyException")
        void whenMinorUnitsIsNegative_thenThrowsInvalidMoneyException() {
            assertThatThrownBy(() -> new Money(-1, CurrencyCode.of("EUR"))).isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName("when minor units is 0 and the currency is valid - then the record holds 0 minor units")
        void whenMinorUnitsIsZeroAndCurrencyIsValid_thenRecordHoldsZeroMinorUnits() {
            Money money = new Money(0, CurrencyCode.of("EUR"));

            assertThat(money.minorUnits()).isZero();
        }
    }

    @Nested
    @DisplayName("computing the decimal amount")
    class Amount {

        @Test
        @DisplayName(
                "when there are 1250 minor units of EUR, a currency with two fraction digits - then it compares equal to 12.50")
        void whenMinorUnitsIs1250OfEur_thenAmountComparesEqualTo1250() {
            Money money = new Money(1250, CurrencyCode.of("EUR"));

            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("12.50"));
        }

        @Test
        @DisplayName(
                "when there are 1200 minor units of JPY, a currency with no fraction digits - then it compares equal to 1200")
        void whenMinorUnitsIs1200OfJpy_thenAmountComparesEqualTo1200() {
            Money money = new Money(1200, CurrencyCode.of("JPY"));

            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("1200"));
        }
    }

    @Nested
    @DisplayName("building money from major units")
    class OfMajorUnits {

        @ParameterizedTest(name = "when the amount is {0} {1} - then it carries {2} minor units")
        @DisplayName("when the amount is within the currency's own precision - then it is accepted and scaled")
        @CsvSource({
            "7200, HUF, 720000",
            "7200.50, HUF, 720050",
            "12.50, EUR, 1250",
            "12.5, EUR, 1250",
            "1200, JPY, 1200",
            "0, EUR, 0"
        })
        void whenAmountIsWithinCurrencyPrecision_thenReturnsMoneyCarryingScaledMinorUnits(
                BigDecimal amount, String currencyCode, long expectedMinorUnits) {
            CurrencyCode currency = CurrencyCode.of(currencyCode);

            Money money = Money.ofMajorUnits(amount, currency);

            assertThat(money.minorUnits()).isEqualTo(expectedMinorUnits);
            assertThat(money.currencyCode()).isEqualTo(currency);
        }

        @Test
        @DisplayName("when the amount is null - then throws InvalidMoneyException")
        void whenAmountIsNull_thenThrowsInvalidMoneyException() {
            assertThatThrownBy(() -> Money.ofMajorUnits(null, CurrencyCode.of("EUR")))
                    .isInstanceOf(InvalidMoneyException.class);
        }

        @Test
        @DisplayName(
                "when the currency is XAU, whose default fraction digits is -1 - then throws InvalidMoneyException naming XAU")
        void whenCurrencyHasNegativeFractionDigits_thenThrowsInvalidMoneyExceptionNamingCurrency() {
            assertThatThrownBy(() -> Money.ofMajorUnits(new BigDecimal("1.00"), CurrencyCode.of("XAU")))
                    .isInstanceOf(InvalidMoneyException.class)
                    .hasMessage("XAU is not a currency an amount can be recorded in");
        }

        @Test
        @DisplayName(
                "when the amount is 12.505 EUR, more precise than the currency - then throws InvalidMoneyException naming the amount, the currency and its decimal places")
        void whenAmountIsMorePreciseThanCurrency_thenThrowsInvalidMoneyExceptionNamingAmountCurrencyAndDecimalPlaces() {
            assertThatThrownBy(() -> Money.ofMajorUnits(new BigDecimal("12.505"), CurrencyCode.of("EUR")))
                    .isInstanceOf(InvalidMoneyException.class)
                    .hasMessage("12.505 is more precise than EUR, which has 2 decimal places");
        }

        @Test
        @DisplayName(
                "when the amount's minor units exceed Long.MAX_VALUE - then throws InvalidMoneyException saying it is too large, never ArithmeticException")
        void whenMinorUnitsOverflowLong_thenThrowsInvalidMoneyExceptionSayingAmountTooLarge() {
            assertThatThrownBy(() -> Money.ofMajorUnits(new BigDecimal("999999999999999999"), CurrencyCode.of("EUR")))
                    .isInstanceOf(InvalidMoneyException.class)
                    .hasMessage("Amount is too large to record");
        }

        @Test
        @DisplayName(
                "when the amount is -1.00 EUR - then throws InvalidMoneyException, since the canonical constructor's non-negative rule still holds")
        void whenAmountIsNegative_thenThrowsInvalidMoneyException() {
            assertThatThrownBy(() -> Money.ofMajorUnits(new BigDecimal("-1.00"), CurrencyCode.of("EUR")))
                    .isInstanceOf(InvalidMoneyException.class);
        }
    }
}
