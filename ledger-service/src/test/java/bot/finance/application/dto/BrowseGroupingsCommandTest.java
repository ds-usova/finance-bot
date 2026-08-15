package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseGroupingsCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);

    @Nested
    @DisplayName("constructing a new browse groupings command")
    class BrowseGroupingsCommandConstructor {

        @Test
        @DisplayName("when the userId is valid - then it reads back unchanged")
        void whenUserIdIsValid_thenItReadsBackUnchanged() {
            BrowseGroupingsCommand browseGroupingsCommand = new BrowseGroupingsCommand(USER_ID);

            assertThat(browseGroupingsCommand.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("when the userId is absent - then throws InvalidUserException")
        void whenUserIdIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new BrowseGroupingsCommand(null)).isInstanceOf(InvalidUserException.class);
        }
    }
}
