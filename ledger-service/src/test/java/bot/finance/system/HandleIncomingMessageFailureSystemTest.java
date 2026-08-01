package bot.finance.system;

import static bot.finance.common.TelegramTestBot.HANDLE_MESSAGE_FAILURE_TOKEN;
import static bot.finance.common.TelegramTestBot.recordedPollsWithOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.telegram.TelegramUpdateListener;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.LogCapture;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.WireMockStubs;
import bot.finance.common.containers.GrpcStubServer;
import io.grpc.Status;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * The bot token below is what isolates this class: a differing property defeats Spring's context cache, so the
 * class gets its own context, a poll loop starting at offset 0, and a {@code /bot<token>/getUpdates} path no
 * other class's poller reaches.
 */
@TestPropertySource(properties = "telegram.bot.token=" + HANDLE_MESSAGE_FAILURE_TOKEN)
class HandleIncomingMessageFailureSystemTest extends AbstractSystemTest {

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String NEXT_OFFSET = String.valueOf(UPDATE_ID + 1);

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private LogCapture logCapture;

    /**
     * The order here is load-bearing: the poll loop is already running, so the appender must be attached before
     * the update-bearing stub exists, or the loop consumes it and logs it into a logger with no appender. The
     * catch-all comes first so the loop never sees a bare 404.
     */
    @BeforeEach
    void attachLogCaptureAndStubTelegramAndConnector() {
        logCapture = LogCapture.attachedTo(TelegramUpdateListener.class);
        WireMockStubs.telegramReturnsNoUpdates(HANDLE_MESSAGE_FAILURE_TOKEN);
        WireMockStubs.telegramReturnsOnFirstPoll(
                HANDLE_MESSAGE_FAILURE_TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, MESSAGE_TEXT)));
        GrpcStubServer.failExtractionWith(Status.UNAVAILABLE.withDescription("AI connector unavailable"));
    }

    @AfterEach
    void detachLogCapture() {
        logCapture.close();
    }

    @Nested
    @DisplayName("unhappy path - the AI connector fails to extract intents")
    class UnhappyPath {

        @Test
        @DisplayName(
                "when the loop picks the update up - then the failure is logged by TelegramUpdateListener and the "
                        + "batch is still confirmed with the follow-up getUpdates offset")
        void whenLoopPicksUpdateUp_thenFailureIsLoggedAndBatchIsStillConfirmed() {
            await("the failure is logged by TelegramUpdateListener")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> {
                        log.debug("Captured log messages: {}", logCapture.messages());

                        assertThat(logCapture.messages())
                                .as("messages logged by TelegramUpdateListener")
                                .anyMatch(message -> message.contains(String.valueOf(UPDATE_ID)));
                    });

            await("a follow-up getUpdates confirms the batch").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedPollsWithOffset(HANDLE_MESSAGE_FAILURE_TOKEN, NEXT_OFFSET))
                    .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                    .isNotEmpty());
        }
    }
}
