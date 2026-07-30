package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidUserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InitializeUserCommandTest {

    @Nested
    @DisplayName("constructing a new user")
    class InitializeUserCommandConstructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the external id is absent, empty, or only whitespace - then throws InvalidUserException")
        void whenExternalIdIsAbsentEmptyOrWhitespace_thenThrowsInvalidUserException(String externalId) {
            assertThatThrownBy(() -> new InitializeUserCommand(externalId)).isInstanceOf(InvalidUserException.class);
        }

        @Test
        @DisplayName("when the external id is non-blank - then the record carries it")
        void whenExternalIdIsNonBlank_thenTheRecordCarriesIt() {
            InitializeUserCommand initializeUserCommand = new InitializeUserCommand("555");

            assertThat(initializeUserCommand.externalId()).isEqualTo("555");
        }
    }
}
