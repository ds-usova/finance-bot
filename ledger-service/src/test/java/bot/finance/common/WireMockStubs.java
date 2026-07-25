package bot.finance.common;

import bot.finance.common.containers.WireMockSupport;

import static bot.finance.common.TelegramTestBot.getUpdatesPath;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

/**
 * Static helpers for registering WireMock stubs against external partner services.
 *
 * <p>The underlying {@link WireMockSupport#SERVER} is started once for the JVM and its stubs are
 * reset after every test via {@link AbstractSystemTest}'s {@code @AfterEach} hook.
 * Tests call one of these methods in their precondition setup; no lifecycle management is needed
 * inside the test class itself.
 *
 * <p>To add support for a new external service or endpoint, add a new static method here.
 *
 * <h2>Register through the instance, never WireMock's static DSL</h2>
 *
 * Every helper here registers through {@code WireMockSupport.SERVER.stubFor(...)}, and every test verifies
 * through {@code WireMockSupport.SERVER.verify(...)} / {@code SERVER.findAll(...)}. WireMock's static
 * {@code stubFor}/{@code verify}/{@code findAll} target {@code WireMock.defaultInstance}, which points at
 * {@code localhost:8080}; {@code WireMockServer} never reconfigures it, and this module's server binds a
 * <em>dynamic</em> port, so a static call cannot reach it and fails with a connection error before any
 * assertion runs. Only the pure builders — {@code post}, {@code urlPathEqualTo}, {@code okJson},
 * {@code aResponse}, {@code postRequestedFor}, {@code equalTo}, {@code absent} — are safe static imports.
 *
 * <h2>Telegram stubs are token-scoped, and pengrad POSTs</h2>
 *
 * Every Bot API call is an HTTP {@code POST} with an {@code application/x-www-form-urlencoded} body —
 * {@code getUpdates} included — so its parameters are form params, never query params. The bot token is part of
 * the path, which is what keeps each test's poll loop on a path of its own.
 */
public final class WireMockStubs {

    private static final String TELEGRAM_RECOVERY_SCENARIO = "telegram-getUpdates-recovery";
    private static final String RECOVERED = "recovered";

    private static final int UPDATE_BEARING_PRIORITY = 1;
    private static final int CATCH_ALL_PRIORITY = 10;

    private WireMockStubs() {
        // private constructor to prevent instantiation
    }

    /**
     * The low-priority catch-all: every {@code getUpdates} poll for this token gets an empty batch, so a poll
     * loop that is merely idling never sees a 404. Register it before any update-bearing stub, which outranks
     * it on priority.
     */
    public static void telegramReturnsNoUpdates(String token) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(getUpdatesPath(token)))
                .atPriority(CATCH_ALL_PRIORITY)
                .willReturn(okJson(TelegramFixtures.noUpdates())));
    }

    /**
     * Answers only the poll that carries no {@code offset} form param — the one pengrad sends until a batch has
     * been confirmed — so the batch is delivered exactly once, without a stateful stub.
     */
    public static void telegramReturnsOnFirstPoll(String token, String responseBody) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(getUpdatesPath(token)))
                .atPriority(UPDATE_BEARING_PRIORITY)
                .withFormParam("offset", absent())
                .willReturn(okJson(responseBody)));
    }

    /**
     * Fails <em>every</em> poll for this token with the same {@code ok:false} body — a persistently failing
     * endpoint, for asserting that the poll loop survives repeated failures rather than terminating.
     */
    public static void telegramFails(String token, int errorCode, String description) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(getUpdatesPath(token)))
                .atPriority(UPDATE_BEARING_PRIORITY)
                .willReturn(okJson(TelegramFixtures.error(errorCode, description))));
    }

    /**
     * Fails the first offset-less poll with an {@code ok:false} body and answers every later one with
     * {@code responseBody}.
     *
     * <p>The only stateful stub in the suite, and it has to be: a failed {@code getUpdates} does not advance
     * pengrad's offset, so the failing poll and the recovering poll are otherwise indistinguishable requests and
     * WireMock would answer both with the same stub. Safe because the scenario state belongs to one token,
     * hence to one test class's own context and poll loop.
     */
    public static void telegramFailsOnceThenReturns(String token, int errorCode, String responseBody) {
        String scenario = "%s-%s".formatted(TELEGRAM_RECOVERY_SCENARIO, token);
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(getUpdatesPath(token)))
                .inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willSetStateTo(RECOVERED)
                .atPriority(UPDATE_BEARING_PRIORITY)
                .withFormParam("offset", absent())
                .willReturn(okJson(TelegramFixtures.error(errorCode, "simulated getUpdates failure"))));

        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(getUpdatesPath(token)))
                .inScenario(scenario)
                .whenScenarioStateIs(RECOVERED)
                .atPriority(UPDATE_BEARING_PRIORITY)
                .withFormParam("offset", absent())
                .willReturn(okJson(responseBody)));
    }

}
