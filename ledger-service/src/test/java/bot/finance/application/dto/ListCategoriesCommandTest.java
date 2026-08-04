package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ListCategoriesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("555");
    private static final String PARENT_CATEGORY_NAME = "Groceries";

    @Nested
    @DisplayName("constructing a new list categories command")
    class ListCategoriesCommandConstructor {

        @Test
        @DisplayName("when the userId and parentCategoryName are valid - then both components read back unchanged")
        void whenUserIdAndParentCategoryNameAreValid_thenBothComponentsReadBackUnchanged() {
            ListCategoriesCommand listCategoriesCommand = new ListCategoriesCommand(USER_ID, PARENT_CATEGORY_NAME);

            assertThat(listCategoriesCommand.userId()).isEqualTo(USER_ID);
            assertThat(listCategoriesCommand.parentCategoryName()).isEqualTo(PARENT_CATEGORY_NAME);
        }

        @Test
        @DisplayName("when the userId is absent - then throws InvalidCategoryException")
        void whenUserIdIsAbsent_thenThrowsInvalidCategoryException() {
            assertThatThrownBy(() -> new ListCategoriesCommand(null, PARENT_CATEGORY_NAME))
                    .isInstanceOf(InvalidCategoryException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the parentCategoryName is absent, empty, or only whitespace - then throws InvalidCategoryException")
        void whenParentCategoryNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidCategoryException(
                String parentCategoryName) {
            assertThatThrownBy(() -> new ListCategoriesCommand(USER_ID, parentCategoryName))
                    .isInstanceOf(InvalidCategoryException.class);
        }
    }
}
