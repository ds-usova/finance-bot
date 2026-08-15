package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.ExpenseFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseExpensesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);
    private static final ExpenseFilter FILTER = new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);

    @Nested
    @DisplayName("constructing a new browse expenses command")
    class BrowseExpensesCommandConstructor {

        @Test
        @DisplayName("when the userId and filter are valid - then both components read back unchanged")
        void whenUserIdAndFilterAreValid_thenBothComponentsReadBackUnchanged() {
            BrowseExpensesCommand browseExpensesCommand = new BrowseExpensesCommand(USER_ID, FILTER);

            assertThat(browseExpensesCommand.userId()).isEqualTo(USER_ID);
            assertThat(browseExpensesCommand.filter()).isEqualTo(FILTER);
        }

        @Test
        @DisplayName("when the userId is absent - then throws InvalidUserException")
        void whenUserIdIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new BrowseExpensesCommand(null, FILTER)).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the filter is absent - then throws InvalidExpenseFilterException")
        void whenFilterIsAbsent_thenThrowsInvalidExpenseFilterException() {
            assertThatThrownBy(() -> new BrowseExpensesCommand(USER_ID, null))
                    .isInstanceOf(InvalidExpenseFilterException.class);
        }
    }
}
