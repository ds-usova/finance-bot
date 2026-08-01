package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnownCategoryTest {

    @Nested
    @DisplayName("constructing the category")
    class Construction {

        @Test
        @DisplayName("when a name and a parent name are both non-blank - then both components read back unchanged")
        void whenNameAndParentNameNonBlank_thenBothComponentsReadBackUnchanged() {
            KnownCategory category = new KnownCategory("Travel", "Insurance");

            assertThat(category.name()).isEqualTo("Travel");
            assertThat(category.parentName()).isEqualTo("Insurance");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when name is null or blank - then throws InvalidValueException")
        void whenNameIsNullOrBlank_thenThrowsInvalidValueException(String name) {
            assertThatThrownBy(() -> new KnownCategory(name, "Insurance"))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when parent name is null or blank - then throws InvalidValueException")
        void whenParentNameIsNullOrBlank_thenThrowsInvalidValueException(String parentName) {
            assertThatThrownBy(() -> new KnownCategory("Travel", parentName))
                    .isInstanceOf(InvalidValueException.class);
        }

    }

    @Nested
    @DisplayName("label()")
    class Label {

        @Test
        @DisplayName("when a category named Travel is under a grouping named Insurance - then label() renders "
                + "\"Insurance > Travel\"")
        void whenCategoryUnderGrouping_thenLabelRendersGroupingArrowCategory() {
            KnownCategory category = new KnownCategory("Travel", "Insurance");

            assertThat(category.label()).isEqualTo("Insurance > Travel");
        }

    }

}
