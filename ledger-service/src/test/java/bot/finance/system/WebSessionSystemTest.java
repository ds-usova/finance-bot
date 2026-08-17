package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.SessionTokens;
import bot.finance.common.fixtures.TelegramLoginPayloads;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.ExpenseStatus;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers the browser session end to end against the fully wired application, and the boundary between it and the
 * MCP endpoint: the two token kinds are signed by one key pair, so nothing but the audience and the way each is
 * carried keeps them apart.
 */
class WebSessionSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;
    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

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
            Response response = RestAssured.given().when().get("/api/v1/session");
            logResponse(response);

            response.then().statusCode(401);
            assertThat(response.getCookie(CSRF_COOKIE))
                    .as("the CSRF cookie a page needs before its first write")
                    .isNotBlank();
        }

        @Test
        @DisplayName("when a genuine Login Widget payload is posted - then 200, a session cookie, and an app_user row")
        void whenAGenuineLoginWidgetPayloadIsPosted_then200ASessionCookieAndAnAppUserRow() {
            String externalId = "web-session-new-user";

            Response response = signIn(externalId);

            response.then().statusCode(200);
            String sessionCookie = response.getCookie(SESSION_COOKIE);
            assertThat(sessionCookie).isNotBlank();
            assertThat(response.jsonPath().getString("externalId")).isEqualTo(externalId);
            long storedUserId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();

            // then: the cookie's token names the stored row's id, not an invented identifier
            assertThat(subjectOf(sessionCookie)).isEqualTo(String.valueOf(storedUserId));
        }

        @Test
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
                    .post("/api/v1/session");
            logResponse(response);

            response.then().statusCode(403);
            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("using and ending a session")
    class UseSession {

        @Test
        @DisplayName("when the session is read with the cookie the sign-in set - then 200 with the signed-in id")
        void whenTheSessionIsReadWithTheCookieTheSignInSet_then200WithTheSignedInId() {
            String externalId = "web-session-read-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);

            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/session");
            logResponse(response);

            response.then().statusCode(200);
            assertThat(response.jsonPath().getString("externalId")).isEqualTo(externalId);
        }

        @Test
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
                    .delete("/api/v1/session");
            logResponse(signOut);

            signOut.then().statusCode(204);
            assertThat(signOut.getDetailedCookie(SESSION_COOKIE).getMaxAge())
                    .as("the cleared session cookie's Max-Age")
                    .isZero();
            assertThat(signOut.getCookie(SESSION_COOKIE)).isBlank();
        }

        @Test
        @Disabled("GI01: the browse GET below calls findPage, which still UNIONs against the dropped "
                + "expense_proposal table until GI01 rewrites it")
        @DisplayName("when a signed-in person browses and refiles - then each acts on their own ledger under "
                + "their user_id")
        void whenASignedInPersonBrowsesAndRefiles_thenEachActsOnTheirOwnLedgerUnderTheirUserId() {
            String externalId = "web-session-browse-refile-user";
            Response signInResponse = signIn(externalId);
            signInResponse.then().statusCode(200);
            String sessionCookie = signInResponse.getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();

            // then: the session's subject is that same user_id
            assertThat(subjectOf(sessionCookie)).isEqualTo(String.valueOf(userId));

            long groupingId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Refiling");
            long firstCategoryId =
                    CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, "Refiled From");
            long secondCategoryId =
                    CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, "Refiled To");
            long expenseId = ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            firstCategoryId,
                            "groceries",
                            "Market",
                            1500L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now(),
                            ExpenseStatus.RECORDED)
                    .id();

            // when: they browse
            Response browseResponse = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/expenses");
            logResponse(browseResponse);

            // then: the browse acts on their own ledger
            browseResponse.then().statusCode(200);
            assertThat(browseResponse.jsonPath().getList("items.description", String.class))
                    .as("the browse lists only this person's own expense")
                    .containsExactly("groceries");

            // when: they refile it
            String csrfToken = freshCsrfToken();
            Response refileResponse = RestAssured.given()
                    .contentType("application/json-patch+json")
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(List.of(Map.of("op", "replace", "path", "/categoryId", "value", secondCategoryId)))
                    .when()
                    .patch("/api/v1/expenses/RECORDED/" + expenseId);
            logResponse(refileResponse);

            // then: the refile acts on their own ledger
            refileResponse.then().statusCode(200);
            assertThat(refileResponse.jsonPath().getLong("categoryId"))
                    .as("the refile's new category")
                    .isEqualTo(secondCategoryId);
        }

        @Test
        @DisplayName("when a pre-change session token names no stored user - then 404 and signing in again works")
        void whenAPreChangeSessionTokenNamesNoStoredUser_then404AndSigningInAgainWorks() {
            String legacySubjectToken = SessionTokens.tokenFor(918_273_645L);

            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, legacySubjectToken)
                    .when()
                    .get("/api/v1/session");
            logResponse(response);

            // then: it is refused as an unknown caller
            response.then().statusCode(404);
            assertThat(response.jsonPath().getString("message"))
                    .as("the refusal names the caller, not the token")
                    .isEqualTo("the caller is unknown");

            // then: signing in again issues a usable session
            String externalId = "web-session-legacy-subject-user";
            String freshSessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            Response readAfterSignIn = RestAssured.given()
                    .cookie(SESSION_COOKIE, freshSessionCookie)
                    .when()
                    .get("/api/v1/session");
            logResponse(readAfterSignIn);
            readAfterSignIn.then().statusCode(200);
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
            long userId = 900001L;
            String mcpToken = McpTokens.tokenFor(accessTokenMinter, userId);

            Response response =
                    RestAssured.given().cookie(SESSION_COOKIE, mcpToken).when().get("/api/v1/session");
            logResponse(response);

            response.then().statusCode(401);
        }

        @Test
        @DisplayName("when a valid MCP token is posted to the MCP endpoint - then it still succeeds")
        void whenAValidMcpTokenIsPostedToTheMcpEndpoint_thenItStillSucceeds() {
            long userId = 900002L;
            String mcpToken = McpTokens.tokenFor(accessTokenMinter, userId);

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
        return BrowserSessions.signIn(BOT_TOKEN, externalId);
    }

    private String freshCsrfToken() {
        return BrowserSessions.csrfToken();
    }

    private Response postSignIn(Map<String, String> payload) {
        return BrowserSessions.postSignIn(payload);
    }

    private String subjectOf(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet().getSubject();
        } catch (ParseException e) {
            throw new IllegalStateException("failed to parse session token", e);
        }
    }
}
