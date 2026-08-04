package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ListCategoriesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId("555");
    private static final String GROUPING_NAME = "Groceries";

    @Nested
    @DisplayName("constructing a new list categories command")
    class ListCategoriesCommandConstructor {

        @Test
        @DisplayName("when the userId and groupingName are valid - then both components read back unchanged")
        void whenUserIdAndGroupingNameAreValid_thenBothComponentsReadBackUnchanged() {
            ListCategoriesCommand listCategoriesCommand = new ListCategoriesCommand(USER_ID, GROUPING_NAME);

            assertThat(listCategoriesCommand.userId()).isEqualTo(USER_ID);
            assertThat(listCategoriesCommand.groupingName()).isEqualTo(GROUPING_NAME);
        }

        @Test
        @DisplayName("when the userId is absent - then throws InvalidGroupingException")
        void whenUserIdIsAbsent_thenThrowsInvalidGroupingException() {
            assertThatThrownBy(() -> new ListCategoriesCommand(null, GROUPING_NAME))
                    .isInstanceOf(InvalidGroupingException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName(
                "when the groupingName is absent, empty, or only whitespace - then throws InvalidGroupingException")
        void whenGroupingNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidGroupingException(String groupingName) {
            assertThatThrownBy(() -> new ListCategoriesCommand(USER_ID, groupingName))
                    .isInstanceOf(InvalidGroupingException.class);
        }
    }
}
