package bot.finance.adapter.telegram;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.telegram.TelegramBotProperties.Polling;
import bot.finance.common.LogCapture;
import bot.finance.common.containers.WireMockSupport;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static bot.finance.common.TelegramTestBot.SUBSCRIBER_TOKEN;
import static bot.finance.common.TelegramTestBot.forToken;
import static bot.finance.common.TelegramTestBot.getUpdatesPath;
import static bot.finance.common.TelegramTestBot.recordedPolls;
import static bot.finance.common.WireMockStubs.telegramFails;
import static bot.finance.common.WireMockStubs.telegramReturnsNoUpdates;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Outbound-adapter integration test: the subscriber is what drives pengrad's outbound {@code getUpdates} HTTP
 * call, so only the subscriber is wired — against the real WireMock singleton, with the real
 * {@link Slf4jLoggerFactory} and a recording {@link UpdatesListener} fake. Nothing is mocked.
 */
class TelegramLongPollingSubscriberTest {

    private static final int POLL_LIMIT = 25;
    private static final int POLL_TIMEOUT_SECONDS = 1;
    private static final long POLL_SLEEP_MILLIS = 50L;

    private static final int RATE_LIMITED = 429;
    private static final String RATE_LIMITED_DESCRIPTION = "Too Many Requests: retry after 1";

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SETTLE_WINDOW = Duration.ofMillis(500);

    private TelegramBot bot;
    private LogCapture logCapture;
    private TelegramLongPollingSubscriber subscriber;

    @BeforeEach
    void setUp() {
        bot = forToken(SUBSCRIBER_TOKEN);
        logCapture = LogCapture.attachedTo(TelegramLongPollingSubscriber.class);
        TelegramBotProperties properties = new TelegramBotProperties(
                SUBSCRIBER_TOKEN,
                WireMockSupport.baseUrl() + "/bot",
                new Polling(true, POLL_LIMIT, POLL_TIMEOUT_SECONDS, POLL_SLEEP_MILLIS));

        subscriber = new TelegramLongPollingSubscriber(
                bot, new RecordingUpdatesListener(), properties, new Slf4jLoggerFactory());
    }

    @AfterEach
    void tearDown() {
        bot.removeGetUpdatesListener();
        logCapture.close();
        WireMockSupport.SERVER.resetAll();
    }

    private static int getUpdatesRequestCount() {
        return recordedPolls(SUBSCRIBER_TOKEN).size();
    }

    private static void settle() throws InterruptedException {
        Thread.sleep(SETTLE_WINDOW.toMillis());
    }

    @Nested
    @DisplayName("starting the poll loop")
    class Start {

        @Test
        @DisplayName("when start() is called - then getUpdates is polled with the configured limit, timeout and allowed updates, and isRunning() reports true")
        void whenStartIsCalled_thenPollsGetUpdatesWithConfiguredParametersAndReportsRunning() {
            telegramReturnsNoUpdates(SUBSCRIBER_TOKEN);

            subscriber.start();

            await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(WireMockSupport.SERVER.findAll(
                    postRequestedFor(urlPathEqualTo(getUpdatesPath(SUBSCRIBER_TOKEN)))
                            .withFormParam("limit", equalTo(String.valueOf(POLL_LIMIT)))
                            .withFormParam("timeout", equalTo(String.valueOf(POLL_TIMEOUT_SECONDS)))
                            .withFormParam("allowed_updates", containing("message"))))
                    .isNotEmpty());
            assertThat(subscriber.isRunning()).isTrue();
        }

        @Test
        @DisplayName("when every poll fails with an error response - then the loop keeps polling and the failure is logged")
        void whenEveryPollFails_thenKeepsPollingAndLogsTheFailure() {
            telegramFails(SUBSCRIBER_TOKEN, RATE_LIMITED, RATE_LIMITED_DESCRIPTION);

            subscriber.start();

            await().atMost(AWAIT_TIMEOUT)
                    .untilAsserted(() -> assertThat(getUpdatesRequestCount()).isGreaterThanOrEqualTo(2));
            await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(logCapture.messages())
                    .anyMatch(message -> message.contains(RATE_LIMITED_DESCRIPTION)));
        }

    }

    @Nested
    @DisplayName("stopping the poll loop")
    class Stop {

        @Test
        @DisplayName("when stop() is called on a started subscriber - then polling ceases and isRunning() reports false")
        void whenStopIsCalledOnStartedSubscriber_thenPollingCeasesAndReportsNotRunning() throws InterruptedException {
            telegramReturnsNoUpdates(SUBSCRIBER_TOKEN);
            subscriber.start();
            await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> assertThat(getUpdatesRequestCount()).isPositive());

            subscriber.stop();

            settle();
            int requestsAfterStop = getUpdatesRequestCount();
            settle();
            assertThat(getUpdatesRequestCount()).isEqualTo(requestsAfterStop);
            assertThat(subscriber.isRunning()).isFalse();
        }

    }

    @Nested
    @DisplayName("reporting whether the poll loop runs")
    class IsRunning {

        @Test
        @DisplayName("when the subscriber was never started - then isRunning() reports false")
        void whenSubscriberWasNeverStarted_thenReportsNotRunning() {
            assertThat(subscriber.isRunning()).isFalse();
        }

    }

    /**
     * Stands in for {@code TelegramUpdateListener}: a real {@link UpdatesListener} that records the batches it is
     * handed and confirms them all, so the subscriber's poll loop is exercised without dragging the listener's
     * own behaviour into this test.
     */
    private static final class RecordingUpdatesListener implements UpdatesListener {

        private final List<Update> received = new CopyOnWriteArrayList<>();

        @Override
        public int process(List<Update> updates) {
            received.addAll(updates);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }

    }

}
