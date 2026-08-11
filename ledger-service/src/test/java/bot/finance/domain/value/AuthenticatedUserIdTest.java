package bot.finance.domain.value;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthenticatedUserIdTest {

    @Nested
    @DisplayName("constructing an authenticated user id")
    class AuthenticatedUserIdConstructor {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the external id is absent, empty or whitespace-only - then InvalidUserException is thrown")
        @Disabled("RU01: moves onto of(), which parses a subject rather than constructing from a String")
        void whenExternalIdIsAbsentEmptyOrWhitespace_thenThrowsInvalidUserException(String externalId) {
            // assertThatThrownBy(() -> new AuthenticatedUserId(externalId)).isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"user-123", "auth0|abc123"})
        @DisplayName("when the external id is non-blank - then the record carries it unchanged")
        @Disabled("RU01: user-123 and auth0|abc123 are now refusals, not carriers; replaced with numeric subjects "
                + "asserted through of()")
        void whenExternalIdIsNonBlank_thenRecordCarriesItUnchanged(String externalId) {
            // AuthenticatedUserId authenticatedUserId = new AuthenticatedUserId(externalId);
            // assertThat(authenticatedUserId.externalId()).isEqualTo(externalId);
        }
    }
}
