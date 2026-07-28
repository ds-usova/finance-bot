package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
    }

    @Nested
    @DisplayName("comparing users for equality")
    class Equality {

        @Test
        @DisplayName(
                "when a stored user and an unstored user share the same external id - then they are equal and their hash codes match")
        void whenStoredAndUnstoredUserShareExternalId_thenTheyAreEqualAndHashCodesMatch() {
            User stored = User.stored(1L, "external-1");
            User unstored = User.newUser("external-1");

            assertThat(stored).isEqualTo(unstored);
            assertThat(stored.hashCode()).isEqualTo(unstored.hashCode());
        }

        @Test
        @DisplayName(
                "when two stored users share the same database id but differ in external id - then they are not equal")
        void whenTwoStoredUsersShareDatabaseIdButDifferInExternalId_thenTheyAreNotEqual() {
            User first = User.stored(1L, "external-1");
            User second = User.stored(1L, "external-2");

            assertThat(first).isNotEqualTo(second);
        }
    }
}
