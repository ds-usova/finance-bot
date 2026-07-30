package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserTest {

    @Nested
    @DisplayName("creating a new user")
    class NewUserFactory {

        @Test
        @DisplayName("when an external id is given - then returns a user carrying that external id and no database id")
        void whenExternalIdIsGiven_thenReturnsUserCarryingExternalIdAndNoDatabaseId() {
            User user = User.newUser("external-1");

            assertThat(user.externalId()).isEqualTo("external-1");
            assertThat(user.id()).isEmpty();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        @DisplayName("when the external id is absent, empty, or only whitespace - then throws InvalidUserException")
        void whenExternalIdIsAbsentEmptyOrWhitespace_thenThrowsInvalidUserException(String externalId) {
            assertThatThrownBy(() -> User.newUser(externalId)).isInstanceOf(InvalidUserException.class);
        }
    }

    @Nested
    @DisplayName("reconstituting a stored user")
    class StoredFactory {

        @Test
        @DisplayName("when a database id and an external id are given - then returns a user carrying both")
        void whenDatabaseIdAndExternalIdAreGiven_thenReturnsUserCarryingBoth() {
            User user = User.stored(42L, "external-1");

            assertThat(user.id()).contains(42L);
            assertThat(user.externalId()).isEqualTo("external-1");
        }

        @Test
        @DisplayName("when a database id and a blank external id are given - then throws InvalidUserException")
        void whenDatabaseIdAndBlankExternalIdAreGiven_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> User.stored(42L, "   ")).isInstanceOf(InvalidUserException.class);
        }
    }
}
