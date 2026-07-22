package bot.finance.common.containers;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class WireMockSupport {

    public static final WireMockServer SERVER;

    static {
        SERVER = new WireMockServer(wireMockConfig().dynamicPort());
        SERVER.start();
    }

    private WireMockSupport() {}

    public static String baseUrl() {
        return "http://localhost:" + SERVER.port();
    }

}
