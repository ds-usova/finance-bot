package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryRefTest {

    @Nested
    @DisplayName("constructing a CategoryRef")
    class CompactConstructor {

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when id is zero or below - then throws InvalidValueException")
        void whenIdIsZeroOrBelow_thenThrowsInvalidValueException(long id) {
            assertThatThrownBy(() -> new CategoryRef(id, "Groceries")).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when name is null or blank - then throws InvalidValueException")
        void whenNameIsNullOrBlank_thenThrowsInvalidValueException(String name) {
            assertThatThrownBy(() -> new CategoryRef(5L, name)).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when id is positive and name is given - then both read back unchanged")
        void whenIdIsPositiveAndNameIsGiven_thenBothReadBackUnchanged() {
            CategoryRef category = new CategoryRef(5L, "Groceries");

            assertThat(category.id()).isEqualTo(5L);
            assertThat(category.name()).isEqualTo("Groceries");
        }
    }
}
