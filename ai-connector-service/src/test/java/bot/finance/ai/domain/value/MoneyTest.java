package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Nested
    @DisplayName("parsing an amount and currency into Money")
    class MoneyFactory {

        @Test
        @DisplayName("when \"12.50\" and \"EUR\" are given - then returns money holding 1250 minor units")
        void whenAmountAndCurrencyGiven_thenReturnsMoneyHolding1250MinorUnits() {
            Money money = Money.of("12.50", "EUR");

            assertThat(money.minorUnits()).isEqualTo(1250L);
        }

        @Test
        @DisplayName("when \"1500\" and \"JPY\", a zero-decimal currency, are given - then returns money holding "
                + "1500 minor units, proving the exponent comes from the currency and not from a hard-coded 2")
        void whenAmountAndZeroDecimalCurrencyGiven_thenReturnsMoneyHolding1500MinorUnits() {
            Money money = Money.of("1500", "JPY");

            assertThat(money.minorUnits()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("when \"12.5\" and \"EUR\", fewer decimals than the currency's scale, are given - then "
                + "returns money holding 1250 minor units")
        void whenFewerDecimalsThanCurrencyScaleGiven_thenReturnsMoneyHolding1250MinorUnits() {
            Money money = Money.of("12.5", "EUR");

            assertThat(money.minorUnits()).isEqualTo(1250L);
        }

        @Test
        @DisplayName("when \"12.505\" and \"EUR\", more decimals than the currency's scale, are given - then "
                + "throws InvalidValueException rather than rounding silently")
        void whenMoreDecimalsThanCurrencyScaleGiven_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> Money.of("12.505", "EUR")).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"twelve", "1e3", "12,50", "NaN", "Infinity"})
        @DisplayName("when the amount is not a decimal number - then throws InvalidValueException")
        void whenAmountIsNotADecimalNumber_thenThrowsInvalidValueException(String amount) {
            assertThatThrownBy(() -> Money.of(amount, "EUR")).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the amount is negative - then throws InvalidValueException")
        void whenAmountIsNegative_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> Money.of("-12.50", "EUR")).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @MethodSource("nullAmountOrCurrency")
        @DisplayName("when the amount or the currency code is null - then throws InvalidValueException")
        void whenAmountOrCurrencyCodeIsNull_thenThrowsInvalidValueException(String amount, String currencyCode) {
            assertThatThrownBy(() -> Money.of(amount, currencyCode)).isInstanceOf(InvalidValueException.class);
        }

        static Stream<Arguments> nullAmountOrCurrency() {
            return Stream.of(Arguments.of(null, "EUR"), Arguments.of("12.50", null));
        }

    }

    @Nested
    @DisplayName("converting minor units back into a BigDecimal amount")
    class Amount {

        @Test
        @DisplayName("when money holds 1250 minor units in EUR - then amount() returns a BigDecimal comparing "
                + "equal to 12.50 with scale 2")
        void whenMoneyHolds1250MinorUnitsInEur_thenAmountReturnsBigDecimalEqualTo1250WithScale2() {
            Money money = new Money(1250L, CurrencyCode.of("EUR"));

            BigDecimal amount = money.amount();

            assertThat(amount).isEqualByComparingTo(new BigDecimal("12.50"));
            assertThat(amount.scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("when money holds 1500 minor units in JPY - then amount() returns a BigDecimal comparing "
                + "equal to 1500 with scale 0")
        void whenMoneyHolds1500MinorUnitsInJpy_thenAmountReturnsBigDecimalEqualTo1500WithScale0() {
            Money money = new Money(1500L, CurrencyCode.of("JPY"));

            BigDecimal amount = money.amount();

            assertThat(amount).isEqualByComparingTo(new BigDecimal("1500"));
            assertThat(amount.scale()).isEqualTo(0);
        }

    }

}
