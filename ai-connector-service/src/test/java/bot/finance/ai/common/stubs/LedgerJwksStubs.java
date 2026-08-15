package bot.finance.ai.common.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import bot.finance.ai.common.containers.WireMockSupport;
import bot.finance.ai.common.fixtures.CallerTokens;
import com.github.tomakehurst.wiremock.http.Fault;
import java.time.Duration;

/**
 * Static helpers for stubbing the ledger's {@code /.well-known/jwks.json} endpoint, registered through
 * {@link WireMockSupport#SERVER}.
 */
public final class LedgerJwksStubs {

    private static final String JWKS_PATH = "/.well-known/jwks.json";

    private LedgerJwksStubs() {}

    public static void stubKeySet() {
        WireMockSupport.SERVER.stubFor(
                get(urlPathEqualTo(JWKS_PATH)).willReturn(okJson(CallerTokens.publicJwkSetJson())));
    }

    public static void stubKeySetUnreachable() {
        WireMockSupport.SERVER.stubFor(
                get(urlPathEqualTo(JWKS_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    public static void stubKeySetDelayed(Duration delay) {
        WireMockSupport.SERVER.stubFor(get(urlPathEqualTo(JWKS_PATH))
                .willReturn(okJson(CallerTokens.publicJwkSetJson()).withFixedDelay((int) delay.toMillis())));
    }
}
