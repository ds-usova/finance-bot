package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IncomingMessageIdTest {

    @Nested
    @DisplayName("deriving a reference from a conversation and its message")
    class OfConversationAndMessage {

        @Test
        @DisplayName(
                "when of is called with a conversation id and message id - then the value joins them with a colon and is stable")
        void whenCalledWithConversationIdAndInboundMessageId_thenValueJoinsThemWithColonAndIsStable() {
            IncomingMessageId first = IncomingMessageId.of("conversation-1", "42");
            IncomingMessageId second = IncomingMessageId.of("conversation-1", "42");

            assertThat(first.value()).isEqualTo("conversation-1:42");
            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("when of is called for two conversations naming the same inbound message id - then the two "
                + "values differ")
        void whenCalledForTwoConversationsNamingSameInboundMessageId_thenValuesDiffer() {
            IncomingMessageId first = IncomingMessageId.of("conversation-1", "42");
            IncomingMessageId second = IncomingMessageId.of("conversation-2", "42");

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("parsing a reference from text")
    class Of {

        @Test
        @DisplayName("when of is called with the canonical text of a UUID - then the value is accepted and reads "
                + "back byte for byte")
        void whenCalledWithCanonicalUuidText_thenValueEqualsUuidAndRoundTrips() {
            String uuidText = UUID.randomUUID().toString();

            IncomingMessageId reference = IncomingMessageId.of(uuidText);

            assertThat(reference.value()).isEqualTo(uuidText);
            assertThat(IncomingMessageId.of(reference.value())).isEqualTo(reference);
        }

        // Both bound cases are ASCII, where a character is a byte, so neither tells a byte check from a
        // character one. Pinning that difference needs a multi-byte value — 55 'a' and one 'é' is 56
        // characters and 57 bytes. Nothing produces one: the value is two decimal ids from Telegram, or a
        // canonical UUID from before the rename.
        @Test
        @DisplayName("when of is called with a value of exactly 56 bytes - then it is accepted")
        void whenCalledWithValueOfExactly56Bytes_thenItIsAccepted() {
            String value = "a".repeat(IncomingMessageId.MAX_BYTES);

            assertThatCode(() -> IncomingMessageId.of(value)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when of is called with a value of 57 bytes - then InvalidIncomingMessageException is thrown")
        void whenCalledWithValueOf57Bytes_thenThrowsInvalidIncomingMessageException() {
            String value = "a".repeat(IncomingMessageId.MAX_BYTES + 1);

            assertThatThrownBy(() -> IncomingMessageId.of(value)).isInstanceOf(InvalidIncomingMessageException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("when of is called with null, empty or blank text - then InvalidIncomingMessageException is "
                + "thrown")
        void whenCalledWithInvalidText_thenThrowsInvalidIncomingMessageException(String value) {
            assertThatThrownBy(() -> IncomingMessageId.of(value)).isInstanceOf(InvalidIncomingMessageException.class);
        }
    }

    @Nested
    @DisplayName("reading the value")
    class Value {

        @Test
        @DisplayName("when value is read on a reference built from a known string - then it returns that string")
        void whenReadOnReferenceBuiltFromKnownUuid_thenReturnsThatUuid() {
            String value = "conversation-1:42";

            IncomingMessageId reference = new IncomingMessageId(value);

            assertThat(reference.value()).isEqualTo(value);
        }

        @Test
        @DisplayName("when the canonical constructor is called with a null value - then "
                + "InvalidIncomingMessageException is thrown")
        void whenCanonicalConstructorCalledWithNullUuid_thenThrowsInvalidIncomingMessageException() {
            assertThatThrownBy(() -> new IncomingMessageId(null)).isInstanceOf(InvalidIncomingMessageException.class);
        }
    }
}
