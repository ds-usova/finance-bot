package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReadSessionCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);

    @Nested
    @DisplayName("constructing a new read session command")
    class ReadSessionCommandConstructor {

        @Test
        @DisplayName("when the userId is absent - then throws InvalidUserException")
        void whenUserIdIsAbsent_thenThrowsInvalidUserException() {
            assertThatThrownBy(() -> new ReadSessionCommand(null)).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the userId is present - then the component reads back unchanged")
        void whenUserIdIsPresent_thenComponentReadsBackUnchanged() {
            ReadSessionCommand readSessionCommand = new ReadSessionCommand(USER_ID);

            assertThat(readSessionCommand.userId()).isEqualTo(USER_ID);
        }
    }
}
