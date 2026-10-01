package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.api.model.ReadPreferences200Response;
import bot.finance.api.model.ReplacePreferencesRequest;
import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

class PreferencesWebMapperTest {

    @Nested
    @DisplayName("mapping a replace-preferences request onto the use case's command")
    class ToReplacePreferencesCommand {

        @Test
        @DisplayName("when a request carries a lower-cased code - then the command carries it upper-cased and the "
                + "caller it was given")
        void whenRequestCarriesALowerCasedCode_thenTheCommandCarriesItUpperCasedAndTheCallerItWasGiven() {
            ReplacePreferencesRequest request = new ReplacePreferencesRequest("eur");
            AuthenticatedUserId userId = new AuthenticatedUserId(42L);

            ReplacePreferencesCommand command = PreferencesWebMapper.toReplacePreferencesCommand(request, userId);

            assertThat(command.defaultCurrency()).isEqualTo(CurrencyCode.of("EUR"));
            assertThat(command.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("when a request carries a code ISO 4217 does not know - then InvalidMoneyException is thrown "
                + "naming the code")
        void whenRequestCarriesACodeIso4217DoesNotKnow_thenInvalidMoneyExceptionIsThrownNamingTheCode() {
            ReplacePreferencesRequest request = new ReplacePreferencesRequest("XYZ");
            AuthenticatedUserId userId = new AuthenticatedUserId(42L);

            assertThatThrownBy(() -> PreferencesWebMapper.toReplacePreferencesCommand(request, userId))
                    .isInstanceOf(InvalidMoneyException.class)
                    .hasMessageContaining("XYZ");
        }

        @Test
        @DisplayName("when a request carries a code no amount can be recorded in - then InvalidMoneyException "
                + "names the code and says so")
        void
                whenRequestCarriesACodeNoAmountCanBeRecordedIn_thenInvalidMoneyExceptionIsThrownNamingTheCodeAndSayingSo() {
            ReplacePreferencesRequest request = new ReplacePreferencesRequest("XAU");
            AuthenticatedUserId userId = new AuthenticatedUserId(42L);

            assertThatThrownBy(() -> PreferencesWebMapper.toReplacePreferencesCommand(request, userId))
                    .isInstanceOf(InvalidMoneyException.class)
                    .hasMessageContaining("XAU")
                    .hasMessageContaining("no amount can be recorded in");
        }
    }

    @Nested
    @DisplayName("mapping stored preferences onto the read-preferences response")
    class ToResponse {

        @Test
        @DisplayName("when preferences carry a currency - then the response carries it upper-cased")
        void whenPreferencesCarryACurrency_thenTheResponseCarriesItUpperCased() {
            Preferences preferences = new Preferences(Optional.of(CurrencyCode.of("EUR")));

            ReadPreferences200Response response = PreferencesWebMapper.toResponse(preferences);

            assertThat(response.getDefaultCurrency()).isEqualTo(JsonNullable.of("EUR"));
        }

        @Test
        @DisplayName("when preferences carry no currency - then the response carries null")
        void whenPreferencesCarryNoCurrency_thenTheResponseCarriesNull() {
            Preferences preferences = new Preferences(Optional.empty());

            ReadPreferences200Response response = PreferencesWebMapper.toResponse(preferences);

            assertThat(response.getDefaultCurrency()).isEqualTo(JsonNullable.of(null));
        }
    }
}
