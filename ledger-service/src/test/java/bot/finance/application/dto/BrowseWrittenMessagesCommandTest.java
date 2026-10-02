package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseWrittenMessagesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);

    @Nested
    @DisplayName("constructing a new browse written messages command")
    class BrowseWrittenMessagesCommandConstructor {

        @Test
        @DisplayName("when the caller is valid - then it reads back unchanged")
        void whenCallerIsValid_thenItReadsBackUnchanged() {
            BrowseWrittenMessagesCommand command = new BrowseWrittenMessagesCommand(USER_ID);

            assertThat(command.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("when the caller is absent - then throws InvalidUserException")
        void whenCallerIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new BrowseWrittenMessagesCommand(null)).isInstanceOf(InvalidUserException.class);
        }
    }
}
