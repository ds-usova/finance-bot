package bot.finance.ai.common.containers;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;

public final class WireMockSupport {

    public static final WireMockServer SERVER;

    static {
        SERVER = new WireMockServer(wireMockConfig().dynamicPort());
        SERVER.start();
    }

    private WireMockSupport() {}

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }

    /**
     * The OpenAI SDK appends {@code chat/completions} to the configured base URL without a version segment, so
     * {@code /v1} has to be part of the base URL itself — as it is in the real {@code https://api.openai.com/v1}.
     */
    public static String openAiBaseUrl() {
        return baseUrl() + "/v1";
    }
}
