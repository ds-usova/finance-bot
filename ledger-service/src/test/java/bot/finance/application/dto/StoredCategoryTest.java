package bot.finance.application.dto;

import org.junit.jupiter.api.Disabled;
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
        @Disabled("RU03: construct with the two-component constructor")
        void whenIdIsZeroOrNegative_thenThrowsInvalidCategoryException(long id) {
            // assertThatThrownBy(() -> new StoredCategory(id, "Groceries", Optional.empty()))
            //         .isInstanceOf(InvalidCategoryException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is absent, empty, or only whitespace - then throws InvalidCategoryException")
        @Disabled("RU03: construct with the two-component constructor")
        void whenNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidCategoryException(String name) {
            // assertThatThrownBy(() -> new StoredCategory(1L, name, Optional.empty()))
            //         .isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName("when the parentName Optional is null - then throws InvalidCategoryException")
        @Disabled("RU03: delete, the component is gone")
        void whenParentNameOptionalIsNull_thenThrowsInvalidCategoryException() {
            // assertThatThrownBy(() -> new StoredCategory(1L, "Groceries", null))
            //         .isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName("when the id, name and empty parentName are valid - then the record carries them unchanged")
        @Disabled("RU03: delete, replaced by the two-component scenario")
        void whenIdNameAndEmptyParentNameAreValid_thenTheRecordCarriesThemUnchanged() {
            // StoredCategory storedCategory = new StoredCategory(1L, "Groceries", Optional.empty());
            //
            // assertThat(storedCategory.id()).isEqualTo(1L);
            // assertThat(storedCategory.name()).isEqualTo("Groceries");
            // assertThat(storedCategory.parentName()).isEmpty();
        }
    }
}
