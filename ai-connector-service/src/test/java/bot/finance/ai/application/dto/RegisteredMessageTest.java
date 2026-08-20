package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.Embedding;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegisteredMessageTest {

    private static final long MESSAGE_ID = 7L;
    private static final Embedding EMBEDDING = new Embedding(List.of(0.1f, 0.2f));

    @Nested
    @DisplayName("constructing the message")
    class Construction {

        @Test
        @DisplayName("when a message id and a vector are given - then both components read back unchanged")
        void whenMessageIdAndVectorGiven_thenBothComponentsReadBackUnchanged() {
            RegisteredMessage message = new RegisteredMessage(MESSAGE_ID, Optional.of(EMBEDDING));

            assertThat(message.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(message.embedding()).contains(EMBEDDING);
        }

        @Test
        @DisplayName("when a message id and no vector are given - then both components read back unchanged")
        void whenMessageIdAndNoVectorGiven_thenBothComponentsReadBackUnchanged() {
            RegisteredMessage message = new RegisteredMessage(MESSAGE_ID, Optional.empty());

            assertThat(message.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(message.embedding()).isEmpty();
        }

        @Test
        @DisplayName("when the embedding Optional is null - then throws InvalidValueException, never "
                + "NullPointerException")
        void whenEmbeddingOptionalIsNull_thenThrowsInvalidValueExceptionNeverNullPointerException() {
            assertThatThrownBy(() -> new RegisteredMessage(MESSAGE_ID, null)).isInstanceOf(InvalidValueException.class);
        }
    }
}
