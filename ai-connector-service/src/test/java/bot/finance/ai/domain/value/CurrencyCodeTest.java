package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CurrencyCodeTest {

    @Nested
    @DisplayName("parsing an ISO 4217 code into a CurrencyCode")
    class CurrencyCodeFactory {

        @Test
        @DisplayName(
                "when a valid ISO 4217 alphabetic code is given - then returns a currency code holding " + "that code")
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
        @DisplayName(
                "when a code java.util.Currency does not recognize is given - then throws " + "InvalidValueException")
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

    @Nested
    @DisplayName("converting minor units to a decimal amount")
    class ToDecimal {

        static Stream<Arguments> minorUnitScenarios() {
            return Stream.of(
                    Arguments.of(Named.of("EUR, 1550 minor units", CurrencyCode.of("EUR")), 1550L, "15.50"),
                    Arguments.of(Named.of("EUR, 5 minor units", CurrencyCode.of("EUR")), 5L, "0.05"),
                    Arguments.of(Named.of("JPY, 1500 minor units", CurrencyCode.of("JPY")), 1500L, "1500"),
                    Arguments.of(Named.of("BHD, 1234 minor units", CurrencyCode.of("BHD")), 1234L, "1.234"),
                    Arguments.of(Named.of("EUR, zero", CurrencyCode.of("EUR")), 0L, "0.00"),
                    Arguments.of(Named.of("EUR, a negative amount", CurrencyCode.of("EUR")), -1550L, "-15.50"));
        }

        @ParameterizedTest
        @MethodSource("minorUnitScenarios")
        @DisplayName("when toDecimal() is called - "
                + "then it answers the main-unit decimal shifted by the currency's own minor-unit digits")
        void whenToDecimalCalled_thenItAnswersTheMainUnitDecimalShiftedByTheCurrencysOwnMinorUnitDigits(
                CurrencyCode currencyCode, long minorUnits, String expected) {
            assertThat(currencyCode.toDecimal(minorUnits)).isEqualTo(expected);
        }
    }
}
