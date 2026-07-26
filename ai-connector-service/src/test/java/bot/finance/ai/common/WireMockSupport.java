package bot.finance.ai.common;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public final class WireMockSupport {

    public static final WireMockServer SERVER;

    static {
        SERVER = new WireMockServer(wireMockConfig().dynamicPort());
        SERVER.start();
    }

    private WireMockSupport() {
    }

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }

}
