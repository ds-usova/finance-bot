package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.CategoryEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * does: signing in over the real sign-in endpoint and carrying the session cookie it set. It triggers no poll-loop
 * scenario, so it signs with the {@code test} profile's own bot token and needs no {@code @TestPropertySource}
 * override.
 */
class BrowseExpensesSystemTest extends AbstractSystemTest {

    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;

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
                    UUID.randomUUID().toString(),
                    now.minusSeconds(60));
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "dinner",
                    "Cafe",
                    2450L,
                    "EUR",
                    UUID.randomUUID().toString(),
                    now);

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
            assertThat(response.jsonPath().getInt("total"))
                    .as("total row count")
                    .isEqualTo(2);

            // then: each row's money is rendered, not carried as minor units
            assertThat(response.jsonPath().getString("items[0].money.amount"))
                    .as("the pending proposal's rendered amount")
                    .isEqualTo("24.50");
            assertThat(response.jsonPath().getString("items[0].money.currency"))
                    .as("the pending proposal's rendered currency")
                    .isEqualTo("€");
            assertThat(response.jsonPath().getString("items[0].money.separator"))
                    .as("the pending proposal's rendered separator")
                    .isEmpty();
            assertThat(response.jsonPath().getString("items[1].money.amount"))
                    .as("the recorded expense's rendered amount")
                    .isEqualTo("12.30");
            assertThat(response.jsonPath().getString("items[1].money.currency"))
                    .as("the recorded expense's rendered currency")
                    .isEqualTo("€");
            assertThat(response.jsonPath().getString("items[1].money.separator"))
                    .as("the recorded expense's rendered separator")
                    .isEmpty();
            assertThat(items.get(0))
                    .as("no item carries a minor-units field any more")
                    .doesNotContainKey("amountMinorUnits");
            assertThat(items.get(1))
                    .as("no item carries a minor-units field any more")
                    .doesNotContainKey("amountMinorUnits");

            // then: dayTotals holds the recorded expense's UTC day alone, the pending proposal counting towards
            // nothing
            LocalDate recordedDay = LocalDate.ofInstant(
                    Instant.parse(response.jsonPath().getString("items[1].createdAt")), ZoneOffset.UTC);
            List<Map<String, Object>> dayTotals = response.jsonPath().getList("dayTotals");
            assertThat(dayTotals)
                    .as("one day total, for the recorded expense's day only")
                    .hasSize(1);
            assertThat(response.jsonPath().getString("dayTotals[0].day"))
                    .as("the day is the recorded expense's own UTC day")
                    .isEqualTo(recordedDay.toString());
            assertThat(response.jsonPath().getList("dayTotals[0].amounts"))
                    .as("one figure for the day, the pending proposal counting towards nothing")
                    .hasSize(1);
            assertThat(response.jsonPath().getString("dayTotals[0].amounts[0].amount"))
                    .as("the day's total is the recorded expense's amount alone")
                    .isEqualTo("12.30");
            assertThat(response.jsonPath().getString("dayTotals[0].amounts[0].currency"))
                    .as("the day's total currency")
                    .isEqualTo("€");
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
        return BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId);
    }
}
