package bot.finance.adapter.telegram;

import static bot.finance.common.TelegramFixtures.MESSAGE_ID;
import static bot.finance.common.TelegramFixtures.textMessageUpdate;
import static bot.finance.common.TelegramFixtures.textMessageUpdateWithoutFrom;
import static bot.finance.common.TelegramFixtures.updatesResponse;
import static bot.finance.common.TelegramFixtures.voiceMessageUpdate;
import static bot.finance.common.TelegramTestBot.LISTENER_TOKEN;
import static bot.finance.common.TelegramTestBot.forToken;
import static bot.finance.common.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.WireMockStubs.telegramReturnsNoUpdates;
import static bot.finance.common.WireMockStubs.telegramReturnsOnFirstPoll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.ResolveProposalsPort;
import bot.finance.common.containers.WireMockSupport;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.GetUpdates;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Integration test for the inbound Telegram adapter. Its protocol is pengrad's {@code getUpdates} long-poll loop
 * over HTTP, not an HTTP endpoint, so there is no web slice to boot: the test drives a real {@link TelegramBot}
 * pointed at the WireMock singleton and never calls {@code process(...)} itself. Only the inbound port is mocked.
 */
class TelegramUpdateListenerTest {

    private static final int TEXT_UPDATE_ID = 42;
    private static final int VOICE_UPDATE_ID = 43;
    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;
    private static final String CONVERSATION_ID = "555";
    private static final String USER_EXTERNAL_ID = "777";
    private static final String INBOUND_MESSAGE_ID = String.valueOf(MESSAGE_ID);
    private static final String MESSAGE_TEXT = "lunch 12 euro";

    private static final int POLL_LIMIT = 100;
    private static final int POLL_TIMEOUT_SECONDS = 1;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    private HandleIncomingMessagePort handleIncomingMessagePort;
    private TelegramBot bot;
    private TelegramUpdateListener listener;

    @BeforeEach
    void setUp() {
        handleIncomingMessagePort = mock(HandleIncomingMessagePort.class);
        listener = new TelegramUpdateListener(
                handleIncomingMessagePort, mock(ResolveProposalsPort.class), new Slf4jLoggerFactory());
        bot = forToken(LISTENER_TOKEN);
        telegramReturnsNoUpdates(LISTENER_TOKEN);
    }

    @AfterEach
    void tearDown() {
        bot.removeGetUpdatesListener();
        WireMockSupport.SERVER.resetAll();
    }

    private void startLoop() {
        bot.setUpdatesListener(
                listener,
                new GetUpdates().limit(POLL_LIMIT).timeout(POLL_TIMEOUT_SECONDS).allowedUpdates("message"));
    }

    private void awaitFollowUpPollWithOffset(String offset) {
        await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(recordedPollsWithOffset(LISTENER_TOKEN, offset))
                .as("follow-up getUpdates polls carrying offset=%s", offset)
                .isNotEmpty());
    }

    private HandleIncomingMessageCommand awaitSingleHandledCommand() {
        ArgumentCaptor<HandleIncomingMessageCommand> command =
                ArgumentCaptor.forClass(HandleIncomingMessageCommand.class);
        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() -> verify(handleIncomingMessagePort).handle(command.capture()));
        return command.getValue();
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when a text-message update is polled - then the port handles the mapped command and the batch is confirmed")
        void whenTextMessageUpdateIsPolled_thenPortHandlesMappedCommandAndBatchIsConfirmed() {
            telegramReturnsOnFirstPoll(
                    LISTENER_TOKEN, updatesResponse(textMessageUpdate(TEXT_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_TEXT)));

            startLoop();

            HandleIncomingMessageCommand handled = awaitSingleHandledCommand();
            assertThat(handled.userExternalId()).isEqualTo(USER_EXTERNAL_ID);
            assertThat(handled.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(handled.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(handled.text()).isEqualTo(MESSAGE_TEXT);
            awaitFollowUpPollWithOffset("43");
        }
    }

    @Nested
    @DisplayName("error mapping")
    class ErrorMapping {

        @Test
        @DisplayName(
                "when the port rejects the delivered update - then the batch is still confirmed so the loop is not stalled")
        void whenPortRejectsTheDeliveredUpdate_thenBatchIsStillConfirmedSoTheLoopIsNotStalled() {
            doThrow(new InvalidIncomingMessageException("incoming message is invalid"))
                    .when(handleIncomingMessagePort)
                    .handle(any());
            telegramReturnsOnFirstPoll(
                    LISTENER_TOKEN, updatesResponse(textMessageUpdate(TEXT_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_TEXT)));

            startLoop();

            awaitFollowUpPollWithOffset("43");
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName(
                "when a voice-message update is polled - then the port is never called and the batch is still confirmed")
        void whenVoiceMessageUpdateIsPolled_thenPortIsNeverCalledAndBatchIsStillConfirmed() {
            telegramReturnsOnFirstPoll(LISTENER_TOKEN, updatesResponse(voiceMessageUpdate(TEXT_UPDATE_ID, CHAT_ID)));

            startLoop();

            awaitFollowUpPollWithOffset("43");
            verifyNoInteractions(handleIncomingMessagePort);
        }

        @Test
        @DisplayName(
                "when a batch mixing a text and a voice update is polled - then only the text update is handled and the whole batch is confirmed")
        void whenBatchMixingTextAndVoiceUpdateIsPolled_thenOnlyTextUpdateIsHandledAndWholeBatchIsConfirmed() {
            telegramReturnsOnFirstPoll(
                    LISTENER_TOKEN,
                    updatesResponse(
                            textMessageUpdate(TEXT_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_TEXT),
                            voiceMessageUpdate(VOICE_UPDATE_ID, CHAT_ID)));

            startLoop();

            awaitFollowUpPollWithOffset("44");
            HandleIncomingMessageCommand handled = awaitSingleHandledCommand();
            assertThat(handled.userExternalId()).isEqualTo(USER_EXTERNAL_ID);
            assertThat(handled.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(handled.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(handled.text()).isEqualTo(MESSAGE_TEXT);
        }

        @Test
        @DisplayName(
                "when a text-message update without a from is polled - then the port is never called and the batch is still confirmed")
        void whenTextMessageUpdateWithoutFromIsPolled_thenPortIsNeverCalledAndBatchIsStillConfirmed() {
            telegramReturnsOnFirstPoll(
                    LISTENER_TOKEN,
                    updatesResponse(textMessageUpdateWithoutFrom(TEXT_UPDATE_ID, CHAT_ID, MESSAGE_TEXT)));

            startLoop();

            awaitFollowUpPollWithOffset("43");
            verifyNoInteractions(handleIncomingMessagePort);
        }
    }
}
