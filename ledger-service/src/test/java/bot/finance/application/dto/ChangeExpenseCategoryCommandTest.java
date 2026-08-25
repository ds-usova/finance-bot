package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChangeExpenseCategoryCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);
    private static final long ENTRY_ID = 42L;
    private static final long CATEGORY_ID = 7L;

    @Nested
    @DisplayName("the compact constructor")
    class ChangeExpenseCategoryCommandConstructor {

        @Test
        @DisplayName("when every field is within its bounds - then the record carries all three unchanged")
        void whenEveryFieldIsWithinItsBounds_thenTheRecordCarriesAllThreeUnchanged() {
            ChangeExpenseCategoryCommand command = new ChangeExpenseCategoryCommand(USER_ID, ENTRY_ID, CATEGORY_ID);

            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.entryId()).isEqualTo(ENTRY_ID);
            assertThat(command.categoryId()).isEqualTo(CATEGORY_ID);
        }

        @Test
        @DisplayName("when the caller is absent - then throws InvalidExpenseCategoryChangeException naming userId")
        void whenCallerIsAbsent_thenThrowsInvalidExpenseCategoryChangeExceptionNamingUserId() {
            assertThatThrownBy(() -> new ChangeExpenseCategoryCommand(null, ENTRY_ID, CATEGORY_ID))
                    .isInstanceOf(InvalidExpenseCategoryChangeException.class)
                    .hasMessageContaining("userId");
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName(
                "when entryId is zero or negative - then throws InvalidExpenseCategoryChangeException naming id and its bound")
        void whenEntryIdIsZeroOrNegative_thenThrowsInvalidExpenseCategoryChangeExceptionNamingIdAndItsBound(
                long entryId) {
            assertThatThrownBy(() -> new ChangeExpenseCategoryCommand(USER_ID, entryId, CATEGORY_ID))
                    .isInstanceOf(InvalidExpenseCategoryChangeException.class)
                    .hasMessageContaining("id");
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName(
                "when categoryId is zero or negative - then throws InvalidExpenseCategoryChangeException naming categoryId")
        void whenCategoryIdIsZeroOrNegative_thenThrowsInvalidExpenseCategoryChangeExceptionNamingCategoryIdAndItsBound(
                long categoryId) {
            assertThatThrownBy(() -> new ChangeExpenseCategoryCommand(USER_ID, ENTRY_ID, categoryId))
                    .isInstanceOf(InvalidExpenseCategoryChangeException.class)
                    .hasMessageContaining("categoryId");
        }
    }
}
