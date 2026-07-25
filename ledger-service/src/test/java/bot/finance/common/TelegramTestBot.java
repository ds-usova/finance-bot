package bot.finance.common;

import bot.finance.common.containers.WireMockSupport;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.pengrad.telegrambot.TelegramBot;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * The single home for pointing a real pengrad {@link TelegramBot} at the WireMock singleton, for the bot tokens
 * the tests use, and for reading back the {@code getUpdates} polls the stub server recorded.
 *
 * <p>The token is part of every Bot API URL ({@code <apiUrl><token>/<method>}), which makes it the partitioning
 * key for the whole suite: a test that owns a token owns a WireMock path no other test's poll loop can reach,
 * and — for a system test declaring it with {@code @TestPropertySource} — a Spring context, and therefore a poll
 * loop, of its own. Every token constant lives here so a test's stubs and its bot cannot disagree about which
 * one it is using.
 */
public final class TelegramTestBot {

    /**
     * Token owned by {@code TelegramUpdateListenerTest}.
     */
    public static final String LISTENER_TOKEN = "listener-test-token";

    /**
     * Token owned by {@code TelegramLongPollingSubscriberTest}.
     */
    public static final String SUBSCRIBER_TOKEN = "subscriber-test-token";

    /**
     * Token owned by {@code ReceiveTelegramMessageSystemTest}.
     */
    public static final String RECEIVE_MESSAGE_TOKEN = "receive-message-test-token";

    /**
     * Token owned by {@code TelegramPollFailureRecoverySystemTest}.
     */
    public static final String POLL_RECOVERY_TOKEN = "poll-recovery-test-token";

    private static final long UPDATE_LISTENER_SLEEP_MILLIS = 50L;

    private TelegramTestBot() {
    }

    /**
     * The token-scoped path pengrad posts {@code getUpdates} to: {@code /bot<token>/getUpdates}. Stub
     * registration and request verification both go through this, never a hand-written path.
     */
    public static String getUpdatesPath(String token) {
        return "/bot%s/getUpdates".formatted(token);
    }

    /**
     * A {@link TelegramBot} whose {@code apiUrl} points at the WireMock singleton, polling at a short sleep
     * interval so an asynchronous assertion does not have to wait long.
     */
    public static TelegramBot forToken(String token) {
        return new TelegramBot.Builder(token)
                .apiUrl(WireMockSupport.baseUrl() + "/bot")
                .updateListenerSleep(UPDATE_LISTENER_SLEEP_MILLIS)
                .build();
    }

    /**
     * Every {@code getUpdates} poll the stub server recorded for this token.
     */
    public static List<LoggedRequest> recordedPolls(String token) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(getUpdatesPath(token))));
    }

    /**
     * The recorded {@code getUpdates} polls carrying the given {@code offset} form param. Only pengrad's own loop
     * sets that param, and only after the listener confirmed a batch, so a non-empty result is proof the batch
     * was consumed.
     */
    public static List<LoggedRequest> recordedPollsWithOffset(String token, String offset) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(getUpdatesPath(token)))
                .withFormParam("offset", equalTo(offset)));
    }

}
