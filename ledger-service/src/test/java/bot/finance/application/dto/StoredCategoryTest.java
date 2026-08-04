package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidCategoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StoredCategoryTest {

    @Nested
    @DisplayName("constructing a new stored category")
    class StoredCategoryConstructor {

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -100})
        @DisplayName("when the id is zero or negative - then throws InvalidCategoryException")
        void whenIdIsZeroOrNegative_thenThrowsInvalidCategoryException(long id) {
            assertThatThrownBy(() -> new StoredCategory(id, "Groceries")).isInstanceOf(InvalidCategoryException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is absent, empty, or only whitespace - then throws InvalidCategoryException")
        void whenNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidCategoryException(String name) {
            assertThatThrownBy(() -> new StoredCategory(1L, name)).isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName("when the id is positive and the name is non-blank - then both components read back unchanged")
        void whenIdIsPositiveAndNameIsNonBlank_thenBothComponentsReadBackUnchanged() {
            StoredCategory storedCategory = new StoredCategory(1L, "Groceries");

            assertThat(storedCategory.id()).isEqualTo(1L);
            assertThat(storedCategory.name()).isEqualTo("Groceries");
        }
    }
}
