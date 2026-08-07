package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.CategoryEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.TelegramLoginPayloads;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers {@code GET /api/v1/expenses} end to end against the fully wired application, entered the way a browser
 * does: signing in over the real sign-in endpoint and carrying the session cookie it set. The bot token below is
 * the {@code telegram.bot.token} the {@code test} profile already configures by default, so this class needs no
 * {@code @TestPropertySource} override.
 */
class BrowseExpensesSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = "default-test-token";
    private static final String SESSION_COOKIE = "fb_session";
    private static final String CSRF_COOKIE = "XSRF-TOKEN";
    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

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
        @DisplayName("when the list is requested with the session cookie and no filter - then 200 with a page of "
                + "both kinds, newest first")
        void whenTheListIsRequestedWithTheSessionCookieAndNoFilter_then200WithAPageOfBothKindsNewestFirst() {
            String externalId = "browse-expenses-happy-path-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long categoryId = CategoryRowUtils.categoryRowsFor(jdbcAggregateTemplate, userId).stream()
                    .filter(row -> row.parentId() != null)
                    .findFirst()
                    .map(CategoryEntity::id)
                    .orElseThrow();

            Instant now = Instant.now();
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "lunch",
                    "Cafe",
                    1230L,
                    "EUR",
                    UUID.randomUUID(),
                    now.minusSeconds(60));
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate, userId, categoryId, "dinner", "Cafe", 2450L, "EUR", UUID.randomUUID(), now);

            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/expenses");
            logResponse(response);

            // then: the response is 200
            response.then().statusCode(200);

            // then: both kinds appear, newest first
            List<Map<String, Object>> items = response.jsonPath().getList("items");
            assertThat(items)
                    .as("both the recorded expense and the pending proposal are in the page")
                    .hasSize(2);
            assertThat(items.get(0).get("description")).as("newest row first").isEqualTo("dinner");
            assertThat(items.get(1).get("description")).as("oldest row second").isEqualTo("lunch");

            // then: each row carries its status and category id
            assertThat(response.jsonPath().getString("items[0].status"))
                    .as("the pending proposal's status")
                    .isEqualTo("PENDING");
            assertThat(response.jsonPath().getLong("items[0].categoryId"))
                    .as("the pending proposal's category id")
                    .isEqualTo(categoryId);
            assertThat(response.jsonPath().getString("items[1].status"))
                    .as("the recorded expense's status")
                    .isEqualTo("RECORDED");
            assertThat(response.jsonPath().getLong("items[1].categoryId"))
                    .as("the recorded expense's category id")
                    .isEqualTo(categoryId);

            // then: the total counts every row the person has
            assertThat(response.jsonPath().getInt("total")).as("total row count").isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the list is requested with no session cookie - then 401 and no row is read")
        void whenTheListIsRequestedWithNoSessionCookie_then401AndNoRowIsRead() {
            Response response = RestAssured.given().when().get("/api/v1/expenses");
            logResponse(response);

            // then: the response is 401 and no row is read
            response.then().statusCode(401);
            assertThat(response.getBody().asString())
                    .as("an unauthenticated request reads back no row")
                    .isBlank();
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
