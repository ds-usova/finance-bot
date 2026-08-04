package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidCategoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

    @Nested
    @DisplayName("constructing a category")
    class CategoryConstructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is absent, empty, or only whitespace - then throws InvalidCategoryException")
        void whenNameIsAbsentEmptyOrOnlyWhitespace_thenThrowsInvalidCategoryException(String name) {
            assertThatThrownBy(() -> new Category(name)).isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName("when a non-blank name is given - then name() reads it back unchanged")
        void whenNonBlankNameIsGiven_thenNameReadsItBackUnchanged() {
            Category category = new Category("Groceries");

            assertThat(category.name()).isEqualTo("Groceries");
        }
    }
}
