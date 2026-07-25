package bot.finance.adapter.telegram;

import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.LogCapture;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.TelegramTestBot;
import bot.finance.common.WireMockStubs;
import bot.finance.common.containers.WireMockSupport;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end: the long-polling loop the application itself starts picks a Telegram text message up and drives it
 * all the way to the inbound message port.
 *
 * <p>Nothing here calls {@code HandleIncomingMessagePort.handle(...)}: the {@code TelegramLongPollingSubscriber}
 * bean starts the loop with the Spring context, and that wiring is precisely what this test exists to prove. The
 * test only stubs the Bot API and waits for the running application to act.
 *
 * <p>The class declares its own bot token, which buys it three things at once: a Spring context of its own (a
 * differing property defeats the context cache), therefore a fresh poll loop starting at offset 0, and a WireMock
 * path — {@code /bot<token>/getUpdates} — no other test class's poller can reach.
 */
@TestPropertySource(properties = "telegram.bot.token=" + TelegramTestBot.RECEIVE_MESSAGE_TOKEN)
class ReceiveTelegramMessageSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.RECEIVE_MESSAGE_TOKEN;

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String CONVERSATION_ID = "555";
    private static final String NEXT_OFFSET = "43";

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private LogCapture logCapture;

    /**
     * The order below is load-bearing. The poll loop is already running by the time this method executes, so the
     * appender has to be attached before the update-bearing stub exists — otherwise the loop can consume the
     * update and log it into a logger with no appender, turning a real failure into a flake. The catch-all is
     * registered second so the loop never sees a bare 404 while this test runs.
     */
    @BeforeEach
    void stubTelegram() {
        logCapture = LogCapture.attachedTo(HandleIncomingMessageUseCase.class);
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramReturnsOnFirstPoll(TOKEN, TelegramFixtures.updatesResponse(
                TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, MESSAGE_TEXT)));
    }

    @AfterEach
    void detachLogCapture() {
        logPollLoopState();
        logCapture.close();
    }

    private void logPollLoopState() {
        List<String> polls = WireMockSupport.SERVER
                .findAll(postRequestedFor(urlPathEqualTo(TelegramTestBot.getUpdatesPath(TOKEN))))
                .stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();

        log.debug("getUpdates requests recorded for token {}: {}", TOKEN, polls);
        log.debug("messages captured from {}: {}", HandleIncomingMessageUseCase.class.getName(), logCapture.messages());
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the running poll loop picks up a text message update - then the batch is confirmed and the message is printed")
        void whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted() {
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(WireMockSupport.SERVER.findAll(
                            postRequestedFor(urlPathEqualTo(TelegramTestBot.getUpdatesPath(TOKEN)))
                                    .withFormParam("offset", equalTo(NEXT_OFFSET))))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            await("the message text reaches the inbound message port")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(logCapture.messages())
                            .as("messages logged by the use case")
                            .anySatisfy(message -> assertThat(message)
                                    .contains(MESSAGE_TEXT)
                                    .contains(CONVERSATION_ID)));
        }

    }

}
