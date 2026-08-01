package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class KnownCategoryTest {

    @Nested
    @DisplayName("constructing a new known category")
    class KnownCategoryConstructor {

        @Test
        @DisplayName("when the name and parent name are both non-blank - then both components read back unchanged")
        void whenNameAndParentNameAreBothNonBlank_thenBothComponentsReadBackUnchanged() {
            KnownCategory knownCategory = new KnownCategory("Groceries", "Expenses");

            assertThat(knownCategory.name()).isEqualTo("Groceries");
            assertThat(knownCategory.parentName()).isEqualTo("Expenses");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is null or blank - then throws InvalidExtractionRequestException")
        void whenNameIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String name) {
            assertThatThrownBy(() -> new KnownCategory(name, "Expenses"))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the parent name is null or blank - then throws InvalidExtractionRequestException")
        void whenParentNameIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String parentName) {
            assertThatThrownBy(() -> new KnownCategory("Groceries", parentName))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }
    }
}
