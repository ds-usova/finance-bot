package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MessageReferenceTest {

    @Nested
    @DisplayName("minting a new reference")
    class NewReference {

        @Test
        @DisplayName("when newReference() is called twice - then both carry a non-null UUID and are not equal")
        void whenCalledTwice_thenBothCarryNonNullUuidAndAreNotEqual() {
            MessageReference first = MessageReference.newReference();
            MessageReference second = MessageReference.newReference();

            assertThat(first.value()).isNotNull();
            assertThat(second.value()).isNotNull();
            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("parsing a reference from text")
    class Of {

        @Test
        @DisplayName("when of is called with the canonical text of a UUID - then the value equals that UUID and "
                + "round-trips through of(reference.value().toString())")
        void whenCalledWithCanonicalUuidText_thenValueEqualsUuidAndRoundTrips() {
            UUID uuid = UUID.randomUUID();

            MessageReference reference = MessageReference.of(uuid.toString());

            assertThat(reference.value()).isEqualTo(uuid);
            assertThat(MessageReference.of(reference.value().toString())).isEqualTo(reference);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "not-a-uuid"})
        @DisplayName("when of is called with null, empty, blank or a non-UUID string - then "
                + "InvalidIncomingMessageException is thrown")
        void whenCalledWithInvalidText_thenThrowsInvalidIncomingMessageException(String value) {
            assertThatThrownBy(() -> MessageReference.of(value)).isInstanceOf(InvalidIncomingMessageException.class);
        }
    }

    @Nested
    @DisplayName("reading the value")
    class Value {

        @Test
        @DisplayName("when value() is read on a reference built from a known UUID - then it returns that UUID")
        void whenReadOnReferenceBuiltFromKnownUuid_thenReturnsThatUuid() {
            UUID uuid = UUID.randomUUID();

            MessageReference reference = new MessageReference(uuid);

            assertThat(reference.value()).isEqualTo(uuid);
        }

        @Test
        @DisplayName("when the canonical constructor is called with a null UUID - then "
                + "InvalidIncomingMessageException is thrown")
        void whenCanonicalConstructorCalledWithNullUuid_thenThrowsInvalidIncomingMessageException() {
            assertThatThrownBy(() -> new MessageReference(null)).isInstanceOf(InvalidIncomingMessageException.class);
        }
    }
}
