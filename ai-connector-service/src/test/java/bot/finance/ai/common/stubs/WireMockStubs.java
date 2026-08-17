package bot.finance.ai.common.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import bot.finance.ai.common.containers.WireMockSupport;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.Duration;

/**
 * Static helpers for stubbing the OpenAI chat-completions and embeddings endpoints, registered through
 * {@link WireMockSupport#SERVER}.
 */
public final class WireMockStubs {

    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";
    public static final String EMBEDDINGS_PATH = "/v1/embeddings";

    private static final String CHAT_COMPLETIONS_SCENARIO = "chat-completions-sequence";

    private WireMockStubs() {}

    /**
     * Serves {@code body} — a full chat-completion response, built with {@link ChatCompletionFixtures} — verbatim
     * to every request.
     */
    public static void stubChatCompletion(String body) {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(okJson(body)));
    }

    public static void stubChatCompletionServerError() {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(serverError()));
    }

    /**
     * Serves {@code bodies} in order, one per successive request — a tool-calling turn is at least two provider
     * round trips (the tool call, then the model's reaction to its result), so a single-response stub cannot
     * cover it. Driven by WireMock scenario states, since the endpoint and method are the same on every request.
     */
    public static void stubChatCompletionSequence(String... bodies) {
        String state = Scenario.STARTED;
        for (int i = 0; i < bodies.length; i++) {
            String nextState = i == bodies.length - 1 ? state : "step-" + (i + 1);
            WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                    .inScenario(CHAT_COMPLETIONS_SCENARIO)
                    .whenScenarioStateIs(state)
                    .willReturn(okJson(bodies[i]))
                    .willSetStateTo(nextState));
            state = nextState;
        }
    }

    /**
     * Serves {@code body} — an embeddings response body, built with
     * {@link bot.finance.ai.common.fixtures.EmbeddingFixtures} — verbatim.
     */
    public static void stubEmbeddings(String body) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(EMBEDDINGS_PATH)).willReturn(okJson(body)));
    }

    public static void stubEmbeddingsServerError() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(EMBEDDINGS_PATH)).willReturn(serverError()));
    }

    /** Serves {@code body} after {@code delay}, for a scenario proving a timeout is honored. */
    public static void stubEmbeddingsDelayed(String body, Duration delay) {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(EMBEDDINGS_PATH)).willReturn(okJson(body).withFixedDelay((int) delay.toMillis())));
    }
}
