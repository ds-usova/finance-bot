package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ExpenseStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChangeExpenseCategoryCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);
    private static final ExpenseStatus STATUS = ExpenseStatus.PENDING;
    private static final long ENTRY_ID = 42L;
    private static final long CATEGORY_ID = 7L;

    @Nested
    @DisplayName("the compact constructor")
    class ChangeExpenseCategoryCommandConstructor {

        @Test
        @DisplayName("when every field is within its bounds - then the record carries all four unchanged")
        @Disabled("R01: the command no longer carries status; retargeted to the id-alone shape by R02")
        void whenEveryFieldIsWithinItsBounds_thenTheRecordCarriesAllFourUnchanged() {
            //            ChangeExpenseCategoryCommand command =
            //                    new ChangeExpenseCategoryCommand(USER_ID, STATUS, ENTRY_ID, CATEGORY_ID);
            //
            //            assertThat(command.userId()).isEqualTo(USER_ID);
            //            assertThat(command.status()).isEqualTo(STATUS);
            //            assertThat(command.entryId()).isEqualTo(ENTRY_ID);
            //            assertThat(command.categoryId()).isEqualTo(CATEGORY_ID);
        }

        @Test
        @DisplayName("when the caller is absent - then throws InvalidExpenseCategoryChangeException naming userId")
        void whenCallerIsAbsent_thenThrowsInvalidExpenseCategoryChangeExceptionNamingUserId() {
            assertThatThrownBy(() -> new ChangeExpenseCategoryCommand(null, ENTRY_ID, CATEGORY_ID))
                    .isInstanceOf(InvalidExpenseCategoryChangeException.class)
                    .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("when the status is absent - then throws InvalidExpenseCategoryChangeException naming status")
        @Disabled("R01: status leaves the command; this scenario has no subject left — deleted by R02")
        void whenStatusIsAbsent_thenThrowsInvalidExpenseCategoryChangeExceptionNamingStatus() {
            //            assertThatThrownBy(() -> new ChangeExpenseCategoryCommand(USER_ID, null, ENTRY_ID,
            // CATEGORY_ID))
            //                    .isInstanceOf(InvalidExpenseCategoryChangeException.class)
            //                    .hasMessageContaining("status");
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
