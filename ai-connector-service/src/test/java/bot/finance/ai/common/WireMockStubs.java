package bot.finance.ai.common;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Static helpers for stubbing the OpenAI chat-completions endpoint.
 *
 * <p>Every helper registers through {@link WireMockSupport#SERVER}, never WireMock's static DSL: that DSL
 * targets {@code localhost:8080}, and this module's server binds a dynamic port, so a static call fails with a
 * connection error before any assertion runs. Only the pure builders — {@code post}, {@code urlPathEqualTo},
 * {@code okJson}, {@code serverError} — are safe to static-import.
 */
public final class WireMockStubs {

    public static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private static final String CHAT_COMPLETIONS_SCENARIO = "chat-completions-sequence";

    private WireMockStubs() {
    }

    /**
     * Serves {@code body} — a full chat-completion response, built with {@link ChatCompletionFixtures} — verbatim
     * to every request. A tool-calling turn needs {@code tool_calls} on {@code choices[0].message}, which is a
     * shape a body escaped into {@code message.content} cannot carry.
     */
    public static void stubChatCompletion(String body) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(okJson(body)));
    }

    public static void stubChatCompletionServerError() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(serverError()));
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

}
