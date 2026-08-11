package bot.finance.common.fixtures;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The browser session as a caller carries it: the cookie and header names it travels under, a cookie holding a
 * freshly minted session token for a MockMvc slice, and the sign-in exchange a system test runs against the booted
 * application.
 */
public final class BrowserSessions {

    public static final String COOKIE_NAME = "fb_session";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private static final String SESSION_PATH = "/api/v1/session";
    private static final Logger log = LoggerFactory.getLogger(BrowserSessions.class);

    private BrowserSessions() {}

    public static Cookie cookieFor(long userId) {
        return new Cookie(COOKIE_NAME, SessionTokens.tokenFor(userId));
    }

    /** The token the unauthenticated read hands out, which a browser gets on page load. */
    public static String csrfToken() {
        return RestAssured.given().when().get(SESSION_PATH).getCookie(CSRF_COOKIE);
    }

    public static Response signIn(String botToken, String externalId) {
        return postSignIn(TelegramLoginPayloads.signedPayload(botToken, externalId));
    }

    public static Response postSignIn(Map<String, String> payload) {
        String csrfToken = csrfToken();

        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie(CSRF_COOKIE, csrfToken)
                .header(CSRF_HEADER, csrfToken)
                .body(payload)
                .when()
                .post(SESSION_PATH);
        log.debug("Response: {} {}", System.lineSeparator(), response.asPrettyString());

        return response;
    }
}
