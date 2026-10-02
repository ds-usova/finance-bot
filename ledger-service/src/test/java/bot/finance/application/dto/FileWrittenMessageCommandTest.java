package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FileWrittenMessageCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);
    private static final String TEXT = "coffee 3.40, taxi home 14";

    @Nested
    @DisplayName("constructing a new file written message command")
    class FileWrittenMessageCommandConstructor {

        @Test
        @DisplayName("when the caller and the text are valid - then both read back unchanged")
        void whenCallerAndTextAreValid_thenBothReadBackUnchanged() {
            FileWrittenMessageCommand command = new FileWrittenMessageCommand(USER_ID, TEXT);

            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.text()).isEqualTo(TEXT);
        }

        @Test
        @DisplayName("when the caller is absent - then throws InvalidUserException")
        void whenCallerIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new FileWrittenMessageCommand(null, TEXT))
                    .isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the text is absent - then throws InvalidWrittenMessageException")
        void whenTextIsAbsent_thenThrowsInvalidWrittenMessageException() {
            assertThatThrownBy(() -> new FileWrittenMessageCommand(USER_ID, null))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }
    }
}
