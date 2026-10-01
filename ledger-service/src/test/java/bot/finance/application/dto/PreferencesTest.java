package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidValueException;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PreferencesTest {

    private static final CurrencyCode CURRENCY_CODE = CurrencyCode.of("EUR");

    @Nested
    @DisplayName("constructing a new preferences")
    class PreferencesConstructor {

        @Test
        @DisplayName("when the defaultCurrency optional is absent - then throws InvalidValueException")
        void whenDefaultCurrencyOptionalIsAbsent_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new Preferences(null)).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the defaultCurrency optional carries a currency - then the component reads back unchanged")
        void whenDefaultCurrencyOptionalCarriesACurrency_thenComponentReadsBackUnchanged() {
            Preferences preferences = new Preferences(Optional.of(CURRENCY_CODE));

            assertThat(preferences.defaultCurrency()).contains(CURRENCY_CODE);
        }

        @Test
        @DisplayName("when the defaultCurrency optional is empty - then the component reads back unchanged")
        void whenDefaultCurrencyOptionalIsEmpty_thenComponentReadsBackUnchanged() {
            Preferences preferences = new Preferences(Optional.empty());

            assertThat(preferences.defaultCurrency()).isEmpty();
        }
    }
}
