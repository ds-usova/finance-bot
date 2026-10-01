package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.UserPreferenceRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers {@code PUT /api/v1/preferences} and {@code GET /api/v1/preferences} end to end against the fully wired
 * application, entered the way a browser does: signing in over the real sign-in endpoint and carrying the session
 * cookie and CSRF token it needs to write.
 */
class SetDefaultCurrencySystemTest extends AbstractSystemTest {

    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;
    private static final String PREFERENCES_PATH = "/api/v1/preferences";

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
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when preferences with no row are replaced with eur and read back - then both answer 200 "
                + "with EUR and one row holds it")
        void whenPreferencesWithNoRowAreReplacedWithEurAndReadBack_thenBothAnswer200WithEurAndOneRowHoldsIt() {
            String externalId = "set-default-currency-happy-path-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userIdOf(externalId);
            String csrfToken = BrowserSessions.csrfToken();

            // given: the person has no preference row
            assertThat(UserPreferenceRowUtils.storedDefaultCurrencyCode(jdbcAggregateTemplate, userId))
                    .as("the person's preference row before any write")
                    .isEmpty();

            // when: they replace their preferences with eur
            Response replaceResponse = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("defaultCurrency", "eur"))
                    .when()
                    .put(PREFERENCES_PATH);
            logResponse(replaceResponse);

            // then: the replace answers 200 with defaultCurrency EUR
            replaceResponse.then().statusCode(200);
            assertThat(replaceResponse.jsonPath().getString("defaultCurrency"))
                    .as("the replace response's defaultCurrency")
                    .isEqualTo("EUR");

            // when: they read their preferences back
            Response readResponse = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get(PREFERENCES_PATH);
            logResponse(readResponse);

            // then: the read answers 200 with defaultCurrency EUR
            readResponse.then().statusCode(200);
            assertThat(readResponse.jsonPath().getString("defaultCurrency"))
                    .as("the read response's defaultCurrency")
                    .isEqualTo("EUR");

            // then: one row holds EUR for that person
            Optional<String> storedCode =
                    UserPreferenceRowUtils.storedDefaultCurrencyCode(jdbcAggregateTemplate, userId);
            assertThat(storedCode).as("the stored preference row").contains("EUR");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when preferences are read with no session cookie - then 401")
        void whenNoSessionCookie_then401() {
            Response response = RestAssured.given().when().get(PREFERENCES_PATH);
            logResponse(response);

            // then: the response is 401
            response.then().statusCode(401);
        }

        @Test
        @DisplayName("when preferences are replaced with no CSRF header - then 403 and the stored preference is "
                + "unchanged")
        void whenNoCsrfHeader_then403AndTheStoredPreferenceIsUnchanged() {
            String externalId = "set-default-currency-no-csrf-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userIdOf(externalId);
            String csrfToken = BrowserSessions.csrfToken();

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .body(Map.of("defaultCurrency", "eur"))
                    .when()
                    .put(PREFERENCES_PATH);
            logResponse(response);

            // then: the response is 403
            response.then().statusCode(403);

            // then: the stored preference is unchanged - still no row
            assertThat(UserPreferenceRowUtils.storedDefaultCurrencyCode(jdbcAggregateTemplate, userId))
                    .as("the person's preference row after the refused write")
                    .isEmpty();
        }
    }

    private Response signIn(String externalId) {
        return BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId);
    }

    private long userIdOf(String externalId) {
        return userEntityRepository.findByExternalId(externalId).orElseThrow().id();
    }
}
