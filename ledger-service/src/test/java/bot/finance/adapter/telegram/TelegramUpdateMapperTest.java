package bot.finance.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.common.TelegramFixtures;
import bot.finance.domain.value.MessageReference;
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

class TelegramUpdateMapperTest {

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;
    private static final int REPORT_MESSAGE_ID = 99;
    private static final String INTERACTION_ID = "callback-query-id";

    @Nested
    @DisplayName("mapping a Telegram update onto the inbound command")
    class ToHandleIncomingMessageCommand {

        @Test
        @DisplayName(
                "when the update carries a from id, a chat id, a message id and non-blank text - then returns a command built from those four components")
        void whenUpdateCarriesFromIdChatIdMessageIdAndNonBlankText_thenReturnsCommandBuiltFromThoseFourComponents() {
            Update update = BotUtils.parseUpdate(
                    TelegramFixtures.textMessageUpdate(UPDATE_ID, USER_ID, CHAT_ID, "lunch 12 euro"));

            Optional<HandleIncomingMessageCommand> command =
                    TelegramUpdateMapper.toHandleIncomingMessageCommand(update);

            assertThat(command)
                    .contains(new HandleIncomingMessageCommand(
                            "777", "555", String.valueOf(TelegramFixtures.MESSAGE_ID), "lunch 12 euro"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("skippableUpdates")
        @DisplayName("when the update carries no usable text message in a chat - then returns an empty Optional")
        void whenUpdateCarriesNoUsableTextMessageInAChat_thenReturnsAnEmptyOptional(
                String description, String updateJson) {
            Update update = BotUtils.parseUpdate(updateJson);

            Optional<HandleIncomingMessageCommand> command =
                    TelegramUpdateMapper.toHandleIncomingMessageCommand(update);

            assertThat(command).isEmpty();
        }

        static Stream<Arguments> skippableUpdates() {
            return Stream.of(
                    arguments("a voice payload and no text", TelegramFixtures.voiceMessageUpdate(UPDATE_ID, CHAT_ID)),
                    arguments("no message at all", TelegramFixtures.callbackQueryUpdate(UPDATE_ID)),
                    arguments(
                            "blank message text",
                            TelegramFixtures.textMessageUpdate(UPDATE_ID, USER_ID, CHAT_ID, "   ")),
                    arguments(
                            "text but no chat",
                            TelegramFixtures.textMessageUpdateWithoutChat(UPDATE_ID, "lunch 12 euro")),
                    arguments(
                            "text and a chat but no from",
                            TelegramFixtures.textMessageUpdateWithoutFrom(UPDATE_ID, CHAT_ID, "lunch 12 euro")));
        }

        @Test
        @DisplayName("when the update is null - then returns an empty Optional")
        void whenUpdateIsNull_thenReturnsAnEmptyOptional() {
            Optional<HandleIncomingMessageCommand> command = TelegramUpdateMapper.toHandleIncomingMessageCommand(null);

            assertThat(command).isEmpty();
        }
    }

    @Nested
    @DisplayName("mapping a Telegram callback query update onto the resolve-proposals command")
    class ToResolveProposalsCommand {

        @Test
        @DisplayName(
                "when a callback query carries accept:<uuid> - then returns an ACCEPT command whose sender comes from from")
        void whenUpdateCarriesCallbackQueryWithAcceptData_thenReturnsCommandWithAcceptResolution() {
            MessageReference reference = MessageReference.newReference();
            Update update = BotUtils.parseUpdate(TelegramFixtures.callbackQueryUpdate(
                    UPDATE_ID, USER_ID, CHAT_ID, REPORT_MESSAGE_ID, "accept:" + reference.value()));

            Optional<ResolveProposalsCommand> command = TelegramUpdateMapper.toResolveProposalsCommand(update);

            assertThat(command)
                    .contains(new ResolveProposalsCommand(
                            "777",
                            "555",
                            String.valueOf(REPORT_MESSAGE_ID),
                            INTERACTION_ID,
                            reference,
                            ProposalResolution.ACCEPT));
        }

        @Test
        @DisplayName(
                "when the same update carries the data discard:<uuid> - then the returned command's resolution is DISCARD")
        void whenSameUpdateCarriesDiscardData_thenReturnedCommandResolutionIsDiscard() {
            MessageReference reference = MessageReference.newReference();
            Update update = BotUtils.parseUpdate(TelegramFixtures.callbackQueryUpdate(
                    UPDATE_ID, USER_ID, CHAT_ID, REPORT_MESSAGE_ID, "discard:" + reference.value()));

            Optional<ResolveProposalsCommand> command = TelegramUpdateMapper.toResolveProposalsCommand(update);

            assertThat(command).isPresent();
            assertThat(command.get().resolution()).isEqualTo(ProposalResolution.DISCARD);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("skippableCallbackUpdates")
        @DisplayName("when the update carries no usable callback query - then returns an empty Optional")
        void whenUpdateIsUnusableForResolution_thenReturnsEmpty(String description, Update update) {
            Optional<ResolveProposalsCommand> command = TelegramUpdateMapper.toResolveProposalsCommand(update);

            assertThat(command).isEmpty();
        }

        static Stream<Arguments> skippableCallbackUpdates() {
            MessageReference reference = MessageReference.newReference();
            return Stream.of(
                    Arguments.of("null update", (Update) null),
                    Arguments.of(
                            "a text-message update with no callback query",
                            BotUtils.parseUpdate(
                                    TelegramFixtures.textMessageUpdate(UPDATE_ID, USER_ID, CHAT_ID, "lunch 12 euro"))),
                    Arguments.of(
                            "a callback query with no from",
                            BotUtils.parseUpdate(TelegramFixtures.callbackQueryUpdateWithoutFrom(
                                    UPDATE_ID, CHAT_ID, "accept:" + reference.value()))),
                    Arguments.of(
                            "a callback query with no message",
                            BotUtils.parseUpdate(TelegramFixtures.callbackQueryUpdateWithoutMessage(
                                    UPDATE_ID, USER_ID, "accept:" + reference.value()))),
                    Arguments.of(
                            "a callback query whose data is noop",
                            BotUtils.parseUpdate(TelegramFixtures.callbackQueryUpdate(
                                    UPDATE_ID, USER_ID, CHAT_ID, REPORT_MESSAGE_ID, "noop"))));
        }
    }
}
