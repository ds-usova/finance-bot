package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidValueException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReplacePreferencesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);
    private static final CurrencyCode CURRENCY_CODE = CurrencyCode.of("EUR");

    @Nested
    @DisplayName("constructing a new replace preferences command")
    class ReplacePreferencesCommandConstructor {

        @Test
        @DisplayName("when the userId is absent - then throws InvalidValueException")
        void whenUserIdIsAbsent_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ReplacePreferencesCommand(null, CURRENCY_CODE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the defaultCurrency is absent - then throws InvalidValueException")
        void whenDefaultCurrencyIsAbsent_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ReplacePreferencesCommand(USER_ID, null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the userId and defaultCurrency are valid - then both components read back unchanged")
        void whenUserIdAndDefaultCurrencyAreValid_thenBothComponentsReadBackUnchanged() {
            ReplacePreferencesCommand replacePreferencesCommand = new ReplacePreferencesCommand(USER_ID, CURRENCY_CODE);

            assertThat(replacePreferencesCommand.userId()).isEqualTo(USER_ID);
            assertThat(replacePreferencesCommand.defaultCurrency()).isEqualTo(CURRENCY_CODE);
        }
    }
}
