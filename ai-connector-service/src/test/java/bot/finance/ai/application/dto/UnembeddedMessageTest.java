package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UnembeddedMessageTest {

    private static final long MESSAGE_ID = 7L;
    private static final String TEXT = "spent 15 euros on lunch";

    @Nested
    @DisplayName("constructing the message")
    class Construction {

        @Test
        @DisplayName("when a message id and a text are given - then both read back unchanged")
        void whenMessageIdAndTextGiven_thenBothReadBackUnchanged() {
            UnembeddedMessage message = new UnembeddedMessage(MESSAGE_ID, TEXT);

            assertThat(message.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(message.text()).isEqualTo(TEXT);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the text is null, empty, or whitespace-only - then throws InvalidValueException")
        void whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException(String text) {
            assertThatThrownBy(() -> new UnembeddedMessage(MESSAGE_ID, text))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
