package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.TelegramLoginPayloads;
import bot.finance.domain.value.Grouping;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Covers {@code GET /api/v1/categories} and {@code GET /api/v1/groupings} end to end against the fully wired
 * application, entered the way a browser does: signing in over the real sign-in endpoint, which also seeds the
 * signed-in person's default category tree ({@link Grouping#defaults()}). The bot token below is the
 * {@code telegram.bot.token} the {@code test} profile already configures by default, so this class needs no
 * {@code @TestPropertySource} override.
 */
class BrowseCategoryTreeSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = "default-test-token";
    private static final String SESSION_COOKIE = "fb_session";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the groupings and categories are requested with the session cookie - then both are 200 "
                + "holding every row, unpaged")
        void whenGroupingsAndCategoriesAreRequestedWithTheSessionCookie_thenBothAre200HoldingEveryRowUnpaged() {
            String externalId = "browse-category-tree-happy-path-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            List<Grouping> defaults = Grouping.defaults();
            int expectedGroupingCount = defaults.size();
            int expectedCategoryCount =
                    defaults.stream().mapToInt(grouping -> grouping.categories().size()).sum();

            Response groupingsResponse = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/groupings");
            logResponse(groupingsResponse);

            // then: the groupings response is 200 holding every seeded grouping, unpaged
            groupingsResponse.then().statusCode(200);
            List<Map<String, Object>> groupings = groupingsResponse.jsonPath().getList("");
            assertThat(groupings).as("every grouping, unpaged").hasSize(expectedGroupingCount);
            Map<Long, String> groupingNamesById = groupings.stream()
                    .collect(Collectors.toMap(
                            grouping -> ((Number) grouping.get("id")).longValue(),
                            grouping -> (String) grouping.get("name")));

            Response categoriesResponse = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/categories");
            logResponse(categoriesResponse);

            // then: the categories response is 200 holding every seeded category, unpaged
            categoriesResponse.then().statusCode(200);
            List<Map<String, Object>> categories = categoriesResponse.jsonPath().getList("");
            assertThat(categories).as("every category, unpaged").hasSize(expectedCategoryCount);

            // then: each category names its grouping's id and name
            assertThat(categories).as("every category names its grouping's id and name").allSatisfy(category -> {
                long groupingId = ((Number) category.get("groupingId")).longValue();
                assertThat(groupingNamesById)
                        .as("category's groupingId resolves to a seeded grouping")
                        .containsKey(groupingId);
                assertThat(category.get("groupingName"))
                        .as("category's groupingName matches the seeded grouping's name")
                        .isEqualTo(groupingNamesById.get(groupingId));
            });
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"/api/v1/groupings", "/api/v1/categories"})
        @DisplayName("when either list is requested with no session cookie - then 401")
        void whenEitherListIsRequestedWithNoSessionCookie_then401(String path) {
            Response response = RestAssured.given().when().get(path);
            logResponse(response);

            // then: the response is 401
            response.then().statusCode(401);
        }
    }

    private Response signIn(String externalId) {
        return postSignIn(TelegramLoginPayloads.signedPayload(BOT_TOKEN, externalId));
    }

    /** The token the unauthenticated read hands out, which a browser gets on page load. */
    private String freshCsrfToken() {
        return RestAssured.given().when().get("/api/v1/session").getCookie(CSRF_COOKIE);
    }

    private Response postSignIn(Map<String, String> payload) {
        String csrfToken = freshCsrfToken();

        RequestSpecification request = RestAssured.given()
                .contentType(ContentType.JSON)
                .cookie(CSRF_COOKIE, csrfToken)
                .header(CSRF_HEADER, csrfToken)
                .body(payload);
        Response response = request.when().post("/api/v1/session");
        logResponse(response);
        return response;
    }
}
