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
        @DisplayName("when every component is non-blank - then all four read back unchanged")
        void whenAllFourComponentsAreNonBlank_thenAllFourComponentsAreReadableUnchanged() {
            HandleIncomingMessageCommand message = new HandleIncomingMessageCommand("42", "555", "1", "lunch 12 euro");

            assertThat(message.userExternalId()).isEqualTo("42");
            assertThat(message.conversationId()).isEqualTo("555");
            assertThat(message.inboundMessageId()).isEqualTo("1");
            assertThat(message.text()).isEqualTo("lunch 12 euro");
        }

        @ParameterizedTest(name = "userExternalId={0}, conversationId={1}, inboundMessageId={2}, text={3}")
        @MethodSource("invalidComponents")
        @DisplayName("when any component is null or blank - then throws InvalidIncomingMessageException")
        void whenAnyComponentIsNullOrBlank_thenThrowsInvalidIncomingMessageException(
                String userExternalId, String conversationId, String inboundMessageId, String text) {
            assertThatThrownBy(() ->
                            new HandleIncomingMessageCommand(userExternalId, conversationId, inboundMessageId, text))
                    .isInstanceOf(InvalidIncomingMessageException.class);
        }

        static Stream<Arguments> invalidComponents() {
            return Stream.of(
                    arguments(null, "555", "1", "text"),
                    arguments("", "555", "1", "text"),
                    arguments("  ", "555", "1", "text"),
                    arguments("42", null, "1", "text"),
                    arguments("42", "", "1", "text"),
                    arguments("42", "  ", "1", "text"),
                    arguments("42", "555", null, "text"),
                    arguments("42", "555", "", "text"),
                    arguments("42", "555", "  ", "text"),
                    arguments("42", "555", "1", null),
                    arguments("42", "555", "1", ""),
                    arguments("42", "555", "1", "  "));
        }
    }
}
