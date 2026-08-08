package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseCategoriesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("555");
    private static final Long GROUPING_ID = 42L;

    @Nested
    @DisplayName("constructing a new browse categories command")
    class BrowseCategoriesCommandConstructor {

        @Test
        @DisplayName("when the userId and groupingId are valid - then both components read back unchanged")
        void whenUserIdAndGroupingIdAreValid_thenBothComponentsReadBackUnchanged() {
            BrowseCategoriesCommand browseCategoriesCommand = new BrowseCategoriesCommand(USER_ID, GROUPING_ID);

            assertThat(browseCategoriesCommand.userId()).isEqualTo(USER_ID);
            assertThat(browseCategoriesCommand.groupingId()).isEqualTo(GROUPING_ID);
        }

        @Test
        @DisplayName("when the groupingId is absent - then it is accepted because the grouping filter is optional")
        void whenGroupingIdIsAbsent_thenItIsAcceptedBecauseTheGroupingFilterIsOptional() {
            BrowseCategoriesCommand browseCategoriesCommand = new BrowseCategoriesCommand(USER_ID, null);

            assertThat(browseCategoriesCommand.userId()).isEqualTo(USER_ID);
            assertThat(browseCategoriesCommand.groupingId()).isNull();
        }

        @Test
        @DisplayName("when the userId is absent - then throws InvalidUserException")
        void whenUserIdIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new BrowseCategoriesCommand(null, GROUPING_ID))
                    .isInstanceOf(InvalidUserException.class);
        }
    }
}
