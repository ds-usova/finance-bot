package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidValueException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReadPreferencesCommandTest {

    private static final AuthenticatedUserId USER_ID = new AuthenticatedUserId(555L);

    @Nested
    @DisplayName("constructing a new read preferences command")
    class ReadPreferencesCommandConstructor {

        @Test
        @DisplayName("when the userId is absent - then throws InvalidValueException")
        void whenUserIdIsAbsent_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ReadPreferencesCommand(null)).isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the userId is present - then the component reads back unchanged")
        void whenUserIdIsPresent_thenComponentReadsBackUnchanged() {
            ReadPreferencesCommand readPreferencesCommand = new ReadPreferencesCommand(USER_ID);

            assertThat(readPreferencesCommand.userId()).isEqualTo(USER_ID);
        }
    }
}
