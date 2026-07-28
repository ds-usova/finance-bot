package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidMoneyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyCodeTest {

    @Nested
    @DisplayName("constructing a currency code")
    class CurrencyCodeConstructor {

        @Test
        @DisplayName("when the code is lowercase - then code() is normalized to upper case")
        void whenCodeIsLowercase_thenCodeIsNormalizedToUpperCase() {
            CurrencyCode currencyCode = new CurrencyCode("eur");

            assertThat(currencyCode.code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the code is not known to ISO 4217 - then InvalidMoneyException is thrown naming the code")
        void whenCodeIsUnknownToIso4217_thenThrowsInvalidMoneyExceptionNamingTheCode() {
            assertThatThrownBy(() -> new CurrencyCode("XYZ"))
                    .isInstanceOf(InvalidMoneyException.class)
                    .hasMessageContaining("XYZ");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"   "})
        @DisplayName("when the code is null or blank - then InvalidMoneyException is thrown")
        void whenCodeIsNullOrBlank_thenThrowsInvalidMoneyException(String code) {
            assertThatThrownBy(() -> new CurrencyCode(code)).isInstanceOf(InvalidMoneyException.class);
        }
    }

    @Nested
    @DisplayName("building a currency code with of()")
    class Of {

        @Test
        @DisplayName("when the code is lowercase - then returns a CurrencyCode equal to the upper-case constructed one")
        void whenCodeIsLowercase_thenReturnsCurrencyCodeEqualToUpperCaseConstructed() {
            CurrencyCode currencyCode = CurrencyCode.of("jpy");

            assertThat(currencyCode).isEqualTo(new CurrencyCode("JPY"));
        }
    }
}
