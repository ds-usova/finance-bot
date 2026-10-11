package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReadWrittenMessageCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);

    @Nested
    @DisplayName("constructing a new read written message command")
    class ReadWrittenMessageCommandConstructor {

        @Test
        @DisplayName("when the caller and a message id of 1 are given - then both read back unchanged")
        void whenCallerAndMessageIdOfOneAreGiven_thenBothReadBackUnchanged() {
            ReadWrittenMessageCommand command = new ReadWrittenMessageCommand(USER_ID, 1L);

            assertThat(command.userId()).isEqualTo(USER_ID);
            assertThat(command.messageId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("when the caller is absent - then throws InvalidUserException")
        void whenCallerIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new ReadWrittenMessageCommand(null, 1L)).isInstanceOf(InvalidUserException.class);
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the message id is below 1 - then throws InvalidWrittenMessageException")
        void whenMessageIdIsBelowOne_thenThrowsInvalidWrittenMessageException(long messageId) {
            assertThatThrownBy(() -> new ReadWrittenMessageCommand(USER_ID, messageId))
                    .isInstanceOf(InvalidWrittenMessageException.class);
        }
    }
}
