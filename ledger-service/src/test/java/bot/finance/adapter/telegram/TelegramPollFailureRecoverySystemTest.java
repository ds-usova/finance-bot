package bot.finance.adapter.telegram;

import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.LogCapture;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.WireMockStubs;
import bot.finance.common.containers.WireMockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static bot.finance.common.TelegramTestBot.POLL_RECOVERY_TOKEN;
import static bot.finance.common.TelegramTestBot.getUpdatesPath;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The poll loop is framework-fired: {@code TelegramLongPollingSubscriber} starts it with the application context,
 * so this test only stubs the Bot API and waits — it never calls {@code HandleIncomingMessagePort.handle(...)}
 * itself.
 *
 * <p>Declaring its own bot token gives this class a Spring context, and therefore a poll loop starting at offset
 * 0, of its own — plus a WireMock path no other class's poller can reach.
 */
@TestPropertySource(properties = "telegram.bot.token=" + POLL_RECOVERY_TOKEN)
class TelegramPollFailureRecoverySystemTest extends AbstractSystemTest {

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String CONFIRMED_OFFSET = String.valueOf(UPDATE_ID + 1);
    private static final int TOO_MANY_REQUESTS = 429;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private LogCapture logCapture;

    /**
     * The order here is load-bearing: the poll loop is already running by the time this executes, so the appender
     * must be attached before any stub can serve the update — otherwise the loop consumes it and logs it into a
     * logger with no appender, and the assertion flakes instead of failing.
     */
    @BeforeEach
    void attachLogCaptureAndStubTelegram() {
        logCapture = LogCapture.attachedTo(HandleIncomingMessageUseCase.class);
        WireMockStubs.telegramReturnsNoUpdates(POLL_RECOVERY_TOKEN);
        WireMockStubs.telegramFailsOnceThenReturns(
                POLL_RECOVERY_TOKEN,
                TOO_MANY_REQUESTS,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, MESSAGE_TEXT)));
    }

    @AfterEach
    void detachLogCapture() {
        logCapture.close();
    }

    @Nested
    @DisplayName("unhappy path - a failed getUpdates does not kill the poll loop")
    class UnhappyPath {

        @Test
        @DisplayName("when the first poll fails with error code 429 - then the loop recovers and the message text is still logged")
        void whenFirstPollFailsWithTooManyRequests_thenLoopRecoversAndMessageTextIsStillLogged() {
            await("the message text is logged after the failed poll")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> {
                        log.debug("Captured log messages: {}", logCapture.messages());

                        assertThat(logCapture.messages())
                                .as("messages logged by the use case once the good response arrived")
                                .anyMatch(message -> message.contains(MESSAGE_TEXT));
                    });

            await("a follow-up getUpdates confirms the batch")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> {
                        log.debug("Recorded getUpdates requests: {}", WireMockSupport.SERVER
                                .findAll(postRequestedFor(urlPathEqualTo(getUpdatesPath(POLL_RECOVERY_TOKEN)))));

                        assertThat(WireMockSupport.SERVER.findAll(
                                postRequestedFor(urlPathEqualTo(getUpdatesPath(POLL_RECOVERY_TOKEN)))
                                        .withFormParam("offset", equalTo(CONFIRMED_OFFSET))))
                                .as("follow-up getUpdates polls carrying offset=%s", CONFIRMED_OFFSET)
                                .isNotEmpty();
                    });
        }

    }

}
