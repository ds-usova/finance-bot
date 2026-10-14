package bot.finance.ai.common.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import bot.finance.ai.common.containers.WireMockSupport;
import java.time.Duration;

/**
 * Stubs the classifier's {@code POST /v1/systemone} endpoint, registered through
 * {@link WireMockSupport#SERVER}, one method per outcome.
 */
public final class ClassifierStubs {

    public static final String SYSTEM_ONE_PATH = "/v1/systemone";

    private ClassifierStubs() {}

    /** Serves {@code body} — an answer built with {@code SystemOneFixtures} — verbatim. */
    public static void stubAnswered(String body) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(SYSTEM_ONE_PATH)).willReturn(okJson(body)));
    }

    public static void stubStatus(int status) {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(SYSTEM_ONE_PATH)).willReturn(aResponse().withStatus(status)));
    }

    /** Serves {@code body} after {@code delay}, for a scenario proving the timeout is honored. */
    public static void stubAnsweredAfter(String body, Duration delay) {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(SYSTEM_ONE_PATH)).willReturn(okJson(body).withFixedDelay((int) delay.toMillis())));
    }

    public static void stubUnparseableBody() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(SYSTEM_ONE_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{not json")));
    }
}
