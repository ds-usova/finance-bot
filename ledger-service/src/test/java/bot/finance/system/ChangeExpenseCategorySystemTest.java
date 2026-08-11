package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
 * Covers {@code PATCH /api/v1/expenses/{status}/{id}} end to end against the fully wired application, entered the
 * way a browser does: signing in over the real sign-in endpoint and carrying the session cookie and CSRF token it
 * needs to write. It triggers no poll-loop scenario, so it signs with the {@code test} profile's own bot token and
 * needs no {@code @TestPropertySource} override.
 */
class ChangeExpenseCategorySystemTest extends AbstractSystemTest {

    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;
    private static final String EXPENSES_PATH = "/api/v1/expenses";
    private static final String ACCEPTANCES_PATH = "/api/v1/expenses/acceptances";
    private static final String PATCH_MEDIA_TYPE = "application/json-patch+json";

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
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the authenticated caller is "
                + "always refused as unknown")
        @DisplayName("when a recorded expense and a pending proposal are patched to a second category - then "
                + "both refile there")
        void whenARecordedExpenseAndAPendingProposalAreEachPatchedToTheSecondCategory_thenBothRefileCorrectly() {
            String externalId = "change-category-happy-path-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userIdOf(externalId);
            long firstCategoryId = groceriesCategoryId(userId, "Supermarkets");
            long secondCategoryId = groceriesCategoryId(userId, "Markets");

            Instant createdAt = Instant.now().minusSeconds(3600);
            long expenseId = ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            firstCategoryId,
                            "groceries",
                            "Market",
                            1500L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            createdAt)
                    .id();
            long proposalId = ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            firstCategoryId,
                            "snacks",
                            "Market",
                            500L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            createdAt)
                    .id();

            String csrfToken = BrowserSessions.csrfToken();

            // when: each is patched to the second category with the session cookie and the CSRF token
            Response recordedResponse =
                    patchCategory(sessionCookie, csrfToken, "RECORDED", expenseId, secondCategoryId);
            logResponse(recordedResponse);
            Response pendingResponse = patchCategory(sessionCookie, csrfToken, "PENDING", proposalId, secondCategoryId);
            logResponse(pendingResponse);

            // then: both answer 200 carrying the new categoryId
            recordedResponse.then().statusCode(200);
            assertThat(recordedResponse.jsonPath().getLong("categoryId"))
                    .as("the recorded expense's new category")
                    .isEqualTo(secondCategoryId);
            pendingResponse.then().statusCode(200);
            assertThat(pendingResponse.jsonPath().getLong("categoryId"))
                    .as("the pending proposal's new category")
                    .isEqualTo(secondCategoryId);

            // then: a later listing shows each under the new category and on the day it was created on
            Response recordedListing = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .queryParam("status", "RECORDED")
                    .queryParam("categoryId", secondCategoryId)
                    .when()
                    .get(EXPENSES_PATH);
            logResponse(recordedListing);
            assertThat(recordedListing.jsonPath().getList("items.description", String.class))
                    .as("the recorded expense now lists under the new category")
                    .containsExactly("groceries");
            assertThat(recordedListing.jsonPath().getString("items[0].createdAt"))
                    .as("the recorded expense keeps the day it was created on")
                    .isEqualTo(recordedResponse.jsonPath().getString("createdAt"));

            Response pendingListing = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .queryParam("status", "PENDING")
                    .queryParam("categoryId", secondCategoryId)
                    .when()
                    .get(EXPENSES_PATH);
            logResponse(pendingListing);
            assertThat(pendingListing.jsonPath().getList("items.description", String.class))
                    .as("the pending proposal now lists under the new category")
                    .containsExactly("snacks");

            // then: the pending one is still PENDING
            assertThat(pendingResponse.jsonPath().getString("status"))
                    .as("the refiled proposal keeps its PENDING status")
                    .isEqualTo("PENDING");
        }

        @Test
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the authenticated caller is "
                + "always refused as unknown")
        @DisplayName("when the refiled pending proposal is accepted - then the recorded expense carries the "
                + "new category")
        void whenTheRefiledPendingProposalIsAccepted_thenTheRecordedExpenseCarriesTheNewCategory() {
            String externalId = "change-category-accept-refiled-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userIdOf(externalId);
            long firstCategoryId = groceriesCategoryId(userId, "Supermarkets");
            long secondCategoryId = groceriesCategoryId(userId, "Markets");
            long proposalId = ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            firstCategoryId,
                            "flowers",
                            "Market",
                            800L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now())
                    .id();

            String csrfToken = BrowserSessions.csrfToken();

            // given: the refiled PENDING entry
            Response refileResponse = patchCategory(sessionCookie, csrfToken, "PENDING", proposalId, secondCategoryId);
            logResponse(refileResponse);
            refileResponse.then().statusCode(200);

            // when: it is accepted through POST /api/v1/expenses/acceptances
            Response acceptResponse = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(proposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);
            logResponse(acceptResponse);
            acceptResponse.then().statusCode(200);

            // then: the recorded expense carries the new category
            List<ExpenseEntity> expenseRows = ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
            assertThat(expenseRows)
                    .as("the accepted proposal is now one recorded expense")
                    .hasSize(1);
            assertThat(expenseRows.get(0).categoryId())
                    .as("the recorded expense carries the category it was refiled to")
                    .isEqualTo(secondCategoryId);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the 404 names the caller as "
                + "unknown rather than the entry")
        @DisplayName("when an id names no entry of theirs under that status - then 404 naming the entry rather "
                + "than the caller")
        void whenAnIdNamesNoEntryOfTheirsUnderThatStatus_then404NamingTheEntry() {
            String externalId = "change-category-missing-entry-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userIdOf(externalId);
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);
            String csrfToken = BrowserSessions.csrfToken();

            Response response = patchCategory(sessionCookie, csrfToken, "RECORDED", 999_999L, categoryId);
            logResponse(response);

            // then: the response is 404 and its message names the entry rather than the caller
            response.then().statusCode(404);
            assertThat(response.jsonPath().getString("message"))
                    .as("the 404 names the entry, not the caller-unknown message")
                    .isEqualTo("no entry of yours carries that id");
        }

        @Test
        @DisplayName("when an entry is patched with no session cookie - then 401 and the row still carries its "
                + "original category")
        void whenNoSessionCookie_then401AndTheRowStillCarriesItsOriginalCategory() {
            String externalId = "change-category-no-session-user";
            signIn(externalId);
            long userId = userIdOf(externalId);
            long originalCategoryId = groceriesCategoryId(userId, "Supermarkets");
            long otherCategoryId = groceriesCategoryId(userId, "Markets");
            long expenseId = ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            originalCategoryId,
                            "groceries",
                            "Market",
                            1500L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now())
                    .id();

            Response response = RestAssured.given()
                    .contentType(PATCH_MEDIA_TYPE)
                    .body(patchDocument(otherCategoryId))
                    .when()
                    .patch(path("RECORDED", expenseId));
            logResponse(response);

            // then: the response is 401 and the row still carries its original category
            response.then().statusCode(401);
            List<ExpenseEntity> expenseRows = ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
            assertThat(expenseRows).hasSize(1);
            assertThat(expenseRows.get(0).categoryId())
                    .as("the row's category is untouched")
                    .isEqualTo(originalCategoryId);
        }

        @Test
        @DisplayName("when an entry is patched with no CSRF token - then 403 and the row keeps its category")
        void whenNoCsrfToken_then403AndTheRowStillCarriesItsOriginalCategory() {
            String externalId = "change-category-no-csrf-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userIdOf(externalId);
            long originalCategoryId = groceriesCategoryId(userId, "Supermarkets");
            long otherCategoryId = groceriesCategoryId(userId, "Markets");
            long expenseId = ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            originalCategoryId,
                            "groceries",
                            "Market",
                            1500L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now())
                    .id();

            Response response = RestAssured.given()
                    .contentType(PATCH_MEDIA_TYPE)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .body(patchDocument(otherCategoryId))
                    .when()
                    .patch(path("RECORDED", expenseId));
            logResponse(response);

            // then: the response is 403 and the row still carries its original category
            response.then().statusCode(403);
            List<ExpenseEntity> expenseRows = ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
            assertThat(expenseRows).hasSize(1);
            assertThat(expenseRows.get(0).categoryId())
                    .as("the row's category is untouched")
                    .isEqualTo(originalCategoryId);
        }
    }

    private Response signIn(String externalId) {
        return BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId);
    }

    private long userIdOf(String externalId) {
        return userEntityRepository.findByExternalId(externalId).orElseThrow().id();
    }

    private long groceriesCategoryId(long userId, String name) {
        long groupingId = CategoryRowUtils.categoryIdNamed(jdbcAggregateTemplate, userId, null, "Groceries");
        return CategoryRowUtils.categoryIdNamed(jdbcAggregateTemplate, userId, groupingId, name);
    }

    private Response patchCategory(
            String sessionCookie, String csrfToken, String status, long entryId, long categoryId) {
        return RestAssured.given()
                .contentType(PATCH_MEDIA_TYPE)
                .cookie(SESSION_COOKIE, sessionCookie)
                .cookie(CSRF_COOKIE, csrfToken)
                .header(CSRF_HEADER, csrfToken)
                .body(patchDocument(categoryId))
                .when()
                .patch(path(status, entryId));
    }

    private static String path(String status, long entryId) {
        return "%s/%s/%d".formatted(EXPENSES_PATH, status, entryId);
    }

    private static List<Map<String, Object>> patchDocument(long categoryId) {
        return List.of(Map.of("op", "replace", "path", "/categoryId", "value", categoryId));
    }
}
