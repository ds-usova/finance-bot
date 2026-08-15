package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class MessageIdentityTest {

    @Nested
    @DisplayName("building a MessageIdentity from a token subject and an incoming message id")
    class MessageIdentityFactory {

        @Test
        @DisplayName("when the subject is a positive decimal and the message id is non-blank - "
                + "then userId matches it and id is unchanged")
        void whenSubjectIsPositiveDecimalAndMessageIdNonBlank_thenIdentityCarriesUserIdAndMessageIdUnchanged() {
            MessageIdentity identity = MessageIdentity.of("42", "msg-123");

            assertThat(identity.userId()).isEqualTo(42L);
            assertThat(identity.incomingMessageId()).isEqualTo("msg-123");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "not-a-number", "99999999999999999999"})
        @DisplayName("when the subject is null, blank, not a number, or overflows long - "
                + "then throws InvalidValueException")
        void whenSubjectIsNullBlankNonNumericOrOverflowing_thenThrowsInvalidValueException(String subject) {
            assertThatThrownBy(() -> MessageIdentity.of(subject, "msg-123")).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the message id is null or blank - then throws InvalidValueException")
        void whenMessageIdIsNullOrBlank_thenThrowsInvalidValueException(String incomingMessageId) {
            assertThatThrownBy(() -> MessageIdentity.of("42", incomingMessageId))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("constructing a MessageIdentity directly")
    class CompactConstructor {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when incomingMessageId is null or blank - then throws InvalidValueException")
        void whenIncomingMessageIdIsNullOrBlank_thenThrowsInvalidValueException(String incomingMessageId) {
            assertThatThrownBy(() -> new MessageIdentity(42L, incomingMessageId))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
