package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidMoneyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            assertThatThrownBy(() -> new Money(-1, CurrencyCode.of("EUR")))
                    .isInstanceOf(InvalidMoneyException.class);
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
        @DisplayName("when there are 1250 minor units of EUR, a currency with two fraction digits - then it compares equal to 12.50")
        void whenMinorUnitsIs1250OfEur_thenAmountComparesEqualTo1250() {
            Money money = new Money(1250, CurrencyCode.of("EUR"));

            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("12.50"));
        }

        @Test
        @DisplayName("when there are 1200 minor units of JPY, a currency with no fraction digits - then it compares equal to 1200")
        void whenMinorUnitsIs1200OfJpy_thenAmountComparesEqualTo1200() {
            Money money = new Money(1200, CurrencyCode.of("JPY"));

            assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("1200"));
        }
    }
}
