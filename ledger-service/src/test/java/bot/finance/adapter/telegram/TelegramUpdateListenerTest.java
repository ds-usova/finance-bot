package bot.finance.adapter.telegram;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static bot.finance.common.fixtures.TelegramFixtures.MESSAGE_ID;
import static bot.finance.common.fixtures.TelegramFixtures.callbackQueryUpdate;
import static bot.finance.common.fixtures.TelegramFixtures.textMessageUpdate;
import static bot.finance.common.fixtures.TelegramFixtures.textMessageUpdateWithoutFrom;
import static bot.finance.common.fixtures.TelegramFixtures.updatesResponse;
import static bot.finance.common.fixtures.TelegramFixtures.voiceMessageUpdate;
import static bot.finance.common.stubs.TelegramTestBot.LISTENER_TOKEN;
import static bot.finance.common.stubs.TelegramTestBot.forToken;
import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.WireMockStubs.telegramDeliversOnce;
import static bot.finance.common.stubs.WireMockStubs.telegramReturnsNoUpdates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.ResolveProposalsPort;
import bot.finance.application.port.TurnMeters;
import bot.finance.common.containers.WireMockSupport;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.IncomingMessageId;
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
    private static final int CALLBACK_UPDATE_ID = 44;
    private static final String CALLBACK_QUERY_ID = "callback-query-id";
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
    private ResolveProposalsPort resolveProposalsPort;
    private TurnMeters turnMeters;
    private TelegramBot bot;
    private TelegramUpdateListener listener;

    @BeforeEach
    void setUp() {
        handleIncomingMessagePort = mock(HandleIncomingMessagePort.class);
        resolveProposalsPort = mock(ResolveProposalsPort.class);
        turnMeters = mock(TurnMeters.class);
        listener = new TelegramUpdateListener(
                handleIncomingMessagePort, resolveProposalsPort, turnMeters, new Slf4jLoggerFactory());
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
                new GetUpdates()
                        .limit(POLL_LIMIT)
                        .timeout(POLL_TIMEOUT_SECONDS)
                        .allowedUpdates("message", "callback_query"));
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

    private ResolveProposalsCommand awaitSingleResolvedCommand() {
        ArgumentCaptor<ResolveProposalsCommand> command = ArgumentCaptor.forClass(ResolveProposalsCommand.class);
        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() -> verify(resolveProposalsPort).resolve(command.capture()));
        return command.getValue();
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when a text-message update is polled - then the port handles the mapped command and the batch is confirmed")
        void whenTextMessageUpdateIsPolled_thenPortHandlesMappedCommandAndBatchIsConfirmed() {
            telegramDeliversOnce(
                    LISTENER_TOKEN, updatesResponse(textMessageUpdate(TEXT_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_TEXT)));

            startLoop();

            HandleIncomingMessageCommand handled = awaitSingleHandledCommand();
            assertThat(handled.userExternalId()).isEqualTo(USER_EXTERNAL_ID);
            assertThat(handled.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(handled.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(handled.text()).isEqualTo(MESSAGE_TEXT);
            awaitFollowUpPollWithOffset("43");
            verify(turnMeters, never()).countUnreported();
        }

        @Test
        @DisplayName("when a callback_query update is polled - then only resolve is called and the batch is confirmed")
        void whenCallbackQueryUpdateIsPolled_thenResolveIsCalledWithMappedCommandAndBatchIsConfirmed() {
            IncomingMessageId reference = newIncomingMessageId();
            telegramDeliversOnce(
                    LISTENER_TOKEN,
                    updatesResponse(callbackQueryUpdate(
                            CALLBACK_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_ID, "accept:" + reference.value())));

            startLoop();

            ResolveProposalsCommand resolved = awaitSingleResolvedCommand();
            assertThat(resolved.userExternalId()).isEqualTo(USER_EXTERNAL_ID);
            assertThat(resolved.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(resolved.reportMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(resolved.interactionId()).isEqualTo(CALLBACK_QUERY_ID);
            assertThat(resolved.reference()).isEqualTo(reference);
            assertThat(resolved.resolution()).isEqualTo(ProposalResolution.ACCEPT);
            verifyNoInteractions(handleIncomingMessagePort);
            awaitFollowUpPollWithOffset(String.valueOf(CALLBACK_UPDATE_ID + 1));
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
            telegramDeliversOnce(
                    LISTENER_TOKEN, updatesResponse(textMessageUpdate(TEXT_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_TEXT)));

            startLoop();

            awaitFollowUpPollWithOffset("43");
            verify(turnMeters, times(1)).countUnreported();
        }

        @Test
        @DisplayName("when resolve throws PersistenceFailedException - then a follow-up poll still confirms the batch")
        void whenResolveThrowsPersistenceFailedException_thenBatchIsStillConfirmed() {
            doThrow(new PersistenceFailedException("simulated persistence failure", new RuntimeException()))
                    .when(resolveProposalsPort)
                    .resolve(any());
            IncomingMessageId reference = newIncomingMessageId();
            telegramDeliversOnce(
                    LISTENER_TOKEN,
                    updatesResponse(callbackQueryUpdate(
                            CALLBACK_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_ID, "accept:" + reference.value())));

            startLoop();

            awaitFollowUpPollWithOffset(String.valueOf(CALLBACK_UPDATE_ID + 1));
            verify(turnMeters, never()).countUnreported();
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName(
                "when a voice-message update is polled - then the port is never called and the batch is still confirmed")
        void whenVoiceMessageUpdateIsPolled_thenPortIsNeverCalledAndBatchIsStillConfirmed() {
            telegramDeliversOnce(LISTENER_TOKEN, updatesResponse(voiceMessageUpdate(TEXT_UPDATE_ID, CHAT_ID)));

            startLoop();

            awaitFollowUpPollWithOffset("43");
            verifyNoInteractions(handleIncomingMessagePort);
        }

        @Test
        @DisplayName("when a batch mixes a text and a voice update - then only the text is handled and the batch "
                + "is confirmed")
        void whenBatchMixingTextAndVoiceUpdateIsPolled_thenOnlyTextUpdateIsHandledAndWholeBatchIsConfirmed() {
            telegramDeliversOnce(
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
            telegramDeliversOnce(
                    LISTENER_TOKEN,
                    updatesResponse(textMessageUpdateWithoutFrom(TEXT_UPDATE_ID, CHAT_ID, MESSAGE_TEXT)));

            startLoop();

            awaitFollowUpPollWithOffset("43");
            verifyNoInteractions(handleIncomingMessagePort);
        }

        @Test
        @DisplayName(
                "when a callback_query carries unrecognised data - then neither port is called and the batch is confirmed")
        void whenCallbackQueryUpdateWithUnrecognisedDataIsPolled_thenNeitherPortIsCalledAndBatchIsStillConfirmed() {
            telegramDeliversOnce(
                    LISTENER_TOKEN,
                    updatesResponse(callbackQueryUpdate(CALLBACK_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_ID, "noop")));

            startLoop();

            awaitFollowUpPollWithOffset(String.valueOf(CALLBACK_UPDATE_ID + 1));
            verifyNoInteractions(handleIncomingMessagePort);
            verifyNoInteractions(resolveProposalsPort);
        }

        @Test
        @DisplayName("when a batch pairs a text and a callback_query update - then each port is called exactly once")
        void
                whenBatchPairingTextAndCallbackQueryUpdateIsPolled_thenEachPortIsCalledExactlyOnceAndWholeBatchIsConfirmed() {
            IncomingMessageId reference = newIncomingMessageId();
            telegramDeliversOnce(
                    LISTENER_TOKEN,
                    updatesResponse(
                            textMessageUpdate(TEXT_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_TEXT),
                            callbackQueryUpdate(
                                    CALLBACK_UPDATE_ID, USER_ID, CHAT_ID, MESSAGE_ID, "discard:" + reference.value())));

            startLoop();

            awaitSingleHandledCommand();
            awaitSingleResolvedCommand();
            awaitFollowUpPollWithOffset(String.valueOf(CALLBACK_UPDATE_ID + 1));
        }
    }
}
