package bot.finance.ai.common;

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

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private WireMockStubs() {
    }

    public static void stubChatCompletion(String extractedJson) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(okJson(ChatCompletionFixtures.chatCompletionResponse(extractedJson))));
    }

    public static void stubChatCompletionServerError() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(serverError()));
    }

    /**
     * A well-formed chat-completions envelope whose {@code content} is not valid {@code ExtractedIntents}
     * JSON — a 200 the transport accepts but structured output cannot map.
     */
    public static void stubMalformedChatCompletion() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(okJson(ChatCompletionFixtures.chatCompletionResponse("not a valid intents payload"))));
    }

}
