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
    @DisplayName("resolving an authenticated user id from a subject")
    class Of {

        @ParameterizedTest
        @ValueSource(strings = {"1", "42"})
        @DisplayName("when the subject is a positive number - then the record carries it as its userId")
        void whenSubjectIsAPositiveNumber_thenRecordCarriesItAsUserId(String subject) {
            AuthenticatedUserId authenticatedUserId = AuthenticatedUserId.of(subject);

            assertThat(authenticatedUserId.userId()).isEqualTo(Long.parseLong(subject));
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-number", "99999999999999999999"})
        @DisplayName("when the subject is not a number, or overflows long - then throws InvalidUserException, "
                + "never NumberFormatException")
        void whenSubjectIsNotANumberOrOverflowsLong_thenThrowsInvalidUserExceptionNeverNumberFormatException(
                String subject) {
            assertThatThrownBy(() -> AuthenticatedUserId.of(subject)).isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the subject is absent, empty or whitespace-only - then throws InvalidUserException")
        void whenSubjectIsAbsentEmptyOrWhitespaceOnly_thenThrowsInvalidUserException(String subject) {
            assertThatThrownBy(() -> AuthenticatedUserId.of(subject)).isInstanceOf(InvalidUserException.class);
        }
    }

    @Nested
    @DisplayName("constructing an authenticated user id")
    class AuthenticatedUserIdConstructor {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the id is zero or negative - then throws InvalidUserException")
        void whenIdIsZeroOrNegative_thenThrowsInvalidUserException(long userId) {
            assertThatThrownBy(() -> new AuthenticatedUserId(userId)).isInstanceOf(InvalidUserException.class);
        }
    }
}
