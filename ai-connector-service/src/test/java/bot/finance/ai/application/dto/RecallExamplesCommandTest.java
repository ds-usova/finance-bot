package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.MessageIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RecallExamplesCommandTest {

    private static final MessageIdentity IDENTITY = new MessageIdentity(42L, "incoming-id");
    private static final String TEXT = "spent 15 euros on lunch";

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Test
        @DisplayName("when an identity and a text are given - then both read back unchanged")
        void whenIdentityAndTextGiven_thenBothReadBackUnchanged() {
            RecallExamplesCommand command = new RecallExamplesCommand(IDENTITY, TEXT);

            assertThat(command.identity()).isEqualTo(IDENTITY);
            assertThat(command.text()).isEqualTo(TEXT);
        }

        @Test
        @DisplayName("when the identity is null - then throws InvalidValueException")
        void whenIdentityIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new RecallExamplesCommand(null, TEXT)).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the text is null, empty, or whitespace-only - then throws InvalidValueException")
        void whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException(String text) {
            assertThatThrownBy(() -> new RecallExamplesCommand(IDENTITY, text))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
