package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyCodeTest {

    @Nested
    @DisplayName("parsing an ISO 4217 code into a CurrencyCode")
    class CurrencyCodeFactory {

        @Test
        @DisplayName("when a valid ISO 4217 alphabetic code is given - then returns a currency code holding "
                + "that code")
        void whenValidIsoAlphabeticCodeGiven_thenReturnsCurrencyCodeHoldingThatCode() {
            CurrencyCode currencyCode = CurrencyCode.of("EUR");

            assertThat(currencyCode.code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when a lowercase valid code is given - then the code is normalized to upper case")
        void whenLowercaseValidCodeGiven_thenCodeIsNormalizedToUpperCase() {
            CurrencyCode currencyCode = CurrencyCode.of("eur");

            assertThat(currencyCode.code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when a code java.util.Currency does not recognize is given - then throws "
                + "InvalidValueException")
        void whenCodeCurrencyDoesNotRecognizeGiven_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> CurrencyCode.of("ABC")).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when null or a blank string is given - then throws InvalidValueException")
        void whenNullOrBlankStringGiven_thenThrowsInvalidValueException(String code) {
            assertThatThrownBy(() -> CurrencyCode.of(code)).isInstanceOf(InvalidValueException.class);
        }

    }

}
