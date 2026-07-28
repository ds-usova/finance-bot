package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.application.dto.IncomingMessage;
import bot.finance.common.TelegramFixtures;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.utility.BotUtils;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TelegramUpdateUtilsTest {

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;

    @Nested
    @DisplayName("mapping a Telegram update onto the inbound command")
    class ToIncomingMessage {

        @Test
        @DisplayName(
                "when the update carries a chat id and non-blank text - then returns a command with the chat id as conversation id and that text")
        void whenUpdateCarriesChatIdAndNonBlankText_thenReturnsCommandWithChatIdAsConversationIdAndThatText() {
            Update update =
                    BotUtils.parseUpdate(TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, "lunch 12 euro"));

            Optional<IncomingMessage> command = TelegramUpdateUtils.toIncomingMessage(update);

            assertThat(command).contains(new IncomingMessage("555", "lunch 12 euro"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("skippableUpdates")
        @DisplayName("when the update carries no usable text message in a chat - then returns an empty Optional")
        void whenUpdateCarriesNoUsableTextMessageInAChat_thenReturnsAnEmptyOptional(
                String description, String updateJson) {
            Update update = BotUtils.parseUpdate(updateJson);

            Optional<IncomingMessage> command = TelegramUpdateUtils.toIncomingMessage(update);

            assertThat(command).isEmpty();
        }

        static Stream<Arguments> skippableUpdates() {
            return Stream.of(
                    arguments("a voice payload and no text", TelegramFixtures.voiceMessageUpdate(UPDATE_ID, CHAT_ID)),
                    arguments("no message at all", TelegramFixtures.callbackQueryUpdate(UPDATE_ID)),
                    arguments("blank message text", TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, "   ")),
                    arguments(
                            "text but no chat",
                            TelegramFixtures.textMessageUpdateWithoutChat(UPDATE_ID, "lunch 12 euro")));
        }

        @Test
        @DisplayName("when the update is null - then returns an empty Optional")
        void whenUpdateIsNull_thenReturnsAnEmptyOptional() {
            Optional<IncomingMessage> command = TelegramUpdateUtils.toIncomingMessage(null);

            assertThat(command).isEmpty();
        }
    }
}
