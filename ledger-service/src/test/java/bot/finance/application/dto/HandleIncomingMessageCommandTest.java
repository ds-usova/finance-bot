package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HandleIncomingMessageCommandTest {

    @Nested
    @DisplayName("constructing an incoming message")
    class HandleIncomingMessageCommandConstructor {

        @Test
        @DisplayName(
                "when the user external id, the conversation id, the inbound message id and the text are non-blank - then all four components are readable unchanged")
        void whenAllFourComponentsAreNonBlank_thenAllFourComponentsAreReadableUnchanged() {
            HandleIncomingMessageCommand message =
                    new HandleIncomingMessageCommand("42", "555", "1", "lunch 12 euro");

            assertThat(message.userExternalId()).isEqualTo("42");
            assertThat(message.conversationId()).isEqualTo("555");
            assertThat(message.inboundMessageId()).isEqualTo("1");
            assertThat(message.text()).isEqualTo("lunch 12 euro");
        }

        @ParameterizedTest(name = "conversationId={0}, text={1}")
        @MethodSource("invalidComponents")
        @DisplayName(
                "when the conversation id or the text is null or blank - then throws InvalidIncomingMessageException")
        void whenConversationIdOrTextIsNullOrBlank_thenThrowsInvalidIncomingMessageException(
                String conversationId, String text) {
            assertThatThrownBy(() -> new HandleIncomingMessageCommand("42", conversationId, "1", text))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        static Stream<Arguments> invalidComponents() {
            return Stream.of(
                    arguments(null, "text"),
                    arguments("", "text"),
                    arguments("  ", "text"),
                    arguments("555", null),
                    arguments("555", ""),
                    arguments("555", "  "));
        }
    }
}
