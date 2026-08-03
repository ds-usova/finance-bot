package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
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
        void whenExternalIdIsAbsentEmptyOrWhitespace_thenThrowsInvalidUserException(String externalId) {
            assertThatThrownBy(() -> new AuthenticatedUserId(externalId)).isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"user-123", "auth0|abc123"})
        @DisplayName("when the external id is non-blank - then the record carries it unchanged")
        void whenExternalIdIsNonBlank_thenRecordCarriesItUnchanged(String externalId) {
            AuthenticatedUserId authenticatedUserId = new AuthenticatedUserId(externalId);

            assertThat(authenticatedUserId.externalId()).isEqualTo(externalId);
        }
    }
}
