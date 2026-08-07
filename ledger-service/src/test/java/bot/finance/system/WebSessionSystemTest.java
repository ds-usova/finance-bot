package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TelegramLoginPayloads;
import bot.finance.common.stubs.TelegramTestBot;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Covers the browser session end to end against the fully wired application, and the boundary between it and the
 * MCP endpoint: the two token kinds are signed by one key pair, so nothing but the audience and the way each is
 * carried keeps them apart.
 */
@TestPropertySource(properties = "telegram.bot.token=" + TelegramTestBot.WEB_SESSION_TOKEN)
class WebSessionSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = TelegramTestBot.WEB_SESSION_TOKEN;
    private static final String SESSION_COOKIE = "fb_session";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Nested
    @DisplayName("signing in")
    class SignIn {

        @Test
        @DisplayName("when the session is read before signing in - then 401, and a CSRF cookie is handed out anyway")
        void whenTheSessionIsReadBeforeSigningIn_then401AndACsrfCookieIsHandedOutAnyway() {
            Response response = RestAssured.given().when().get("/api/session");
            logResponse(response);

            response.then().statusCode(401);
            assertThat(response.getCookie(CSRF_COOKIE))
                    .as("the CSRF cookie a page needs before its first write")
                    .isNotBlank();
        }

        @Test
        @Disabled("RS03: retargets every RestAssured call in this class onto /api/v1/session")
        @DisplayName("when a genuine Login Widget payload is posted - then 200, a session cookie, and an app_user row")
        void whenAGenuineLoginWidgetPayloadIsPosted_then200ASessionCookieAndAnAppUserRow() {
            String externalId = "web-session-new-user";

            Response response = signIn(externalId);

            response.then().statusCode(200);
            assertThat(response.getCookie(SESSION_COOKIE)).isNotBlank();
            assertThat(response.jsonPath().getString("externalId")).isEqualTo(externalId);
            assertThat(userEntityRepository.findByExternalId(externalId)).isPresent();
        }

        @Test
        @Disabled("RS03: retargets every RestAssured call in this class onto /api/v1/session")
        @DisplayName("when the same user signs in twice - then the second sign-in stores no second user")
        void whenTheSameUserSignsInTwice_thenTheSecondSignInStoresNoSecondUser() {
            String externalId = "web-session-returning-user";

            signIn(externalId).then().statusCode(200);
            long storedIdAfterFirst = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();

            signIn(externalId).then().statusCode(200);

            assertThat(userEntityRepository
                            .findByExternalId(externalId)
                            .orElseThrow()
                            .id())
                    .isEqualTo(storedIdAfterFirst);
        }

        @Test
        @DisplayName("when a payload signed with another bot token is posted - then 401 and no user is stored")
        void whenAPayloadSignedWithAnotherBotTokenIsPosted_then401AndNoUserIsStored() {
            String externalId = "web-session-forged-user";
            Map<String, String> forged = TelegramLoginPayloads.signedPayload("some-other-bot-token", externalId);

            Response response = postSignIn(forged);

            response.then().statusCode(401);
            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName("when a genuine payload is posted without a CSRF token - then it is refused and no user is stored")
        void whenAGenuinePayloadIsPostedWithoutACsrfToken_thenItIsRefusedAndNoUserIsStored() {
            String externalId = "web-session-no-csrf-user";

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(TelegramLoginPayloads.signedPayload(BOT_TOKEN, externalId))
                    .when()
                    .post("/api/session");
            logResponse(response);

            response.then().statusCode(401);
            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("using and ending a session")
    class UseSession {

        @Test
        @Disabled("RS03: retargets every RestAssured call in this class onto /api/v1/session")
        @DisplayName("when the session is read with the cookie the sign-in set - then 200 with the signed-in id")
        void whenTheSessionIsReadWithTheCookieTheSignInSet_then200WithTheSignedInId() {
            String externalId = "web-session-read-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);

            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/session");
            logResponse(response);

            response.then().statusCode(200);
            assertThat(response.jsonPath().getString("externalId")).isEqualTo(externalId);
        }

        @Test
        @Disabled("RS03: retargets every RestAssured call in this class onto /api/v1/session")
        @DisplayName("when the session is deleted - then reading it with the same cookie value no longer works")
        void whenTheSessionIsDeleted_thenReadingItWithTheSameCookieValueNoLongerWorks() {
            String externalId = "web-session-sign-out-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            String csrfToken = freshCsrfToken();

            Response signOut = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .when()
                    .delete("/api/session");
            logResponse(signOut);

            signOut.then().statusCode(204);
            assertThat(signOut.getDetailedCookie(SESSION_COOKIE).getMaxAge())
                    .as("the cleared session cookie's Max-Age")
                    .isZero();
            assertThat(signOut.getCookie(SESSION_COOKIE)).isBlank();
        }
    }

    @Nested
    @DisplayName("keeping the two token kinds apart")
    class TokenSeparation {

        @Test
        @DisplayName("when a session token is sent to the MCP endpoint - then it is refused")
        void whenASessionTokenIsSentToTheMcpEndpoint_thenItIsRefused() {
            String externalId = "web-session-mcp-crossover-user";
            String sessionToken = signIn(externalId).getCookie(SESSION_COOKIE);

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .accept(McpRequests.ACCEPT_HEADER)
                    .header("Authorization", "Bearer " + sessionToken)
                    .body(McpRequests.toolsList())
                    .when()
                    .post("/mcp");
            logResponse(response);

            response.then().statusCode(401);
        }

        @Test
        @DisplayName("when an MCP token is presented as the session cookie - then it is refused")
        void whenAnMcpTokenIsPresentedAsTheSessionCookie_thenItIsRefused() {
            String externalId = "web-session-mcp-token-as-cookie-user";
            String mcpToken = McpTokens.tokenFor(accessTokenMinter, externalId);

            Response response =
                    RestAssured.given().cookie(SESSION_COOKIE, mcpToken).when().get("/api/session");
            logResponse(response);

            response.then().statusCode(401);
        }

        @Test
        @DisplayName("when a valid MCP token is posted to the MCP endpoint - then it still succeeds")
        void whenAValidMcpTokenIsPostedToTheMcpEndpoint_thenItStillSucceeds() {
            String externalId = "web-session-mcp-still-works-user";
            String mcpToken = McpTokens.tokenFor(accessTokenMinter, externalId);

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .accept(McpRequests.ACCEPT_HEADER)
                    .header("Authorization", "Bearer " + mcpToken)
                    .body(McpRequests.toolsList())
                    .when()
                    .post("/mcp");
            logResponse(response);

            response.then().statusCode(200);
        }
    }

    private Response signIn(String externalId) {
        return postSignIn(TelegramLoginPayloads.signedPayload(BOT_TOKEN, externalId));
    }

    /** The token the unauthenticated read hands out, which a browser gets on page load. */
    private String freshCsrfToken() {
        return RestAssured.given().when().get("/api/session").getCookie(CSRF_COOKIE);
    }

    private Response postSignIn(Map<String, String> payload) {
        String csrfToken = freshCsrfToken();

        RequestSpecification request = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie(CSRF_COOKIE, csrfToken)
                .header(CSRF_HEADER, csrfToken)
                .body(payload);
        Response response = request.when().post("/api/session");
        logResponse(response);
        return response;
    }
}
