package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.containers.ToxiproxyContainers;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamEntries.ChangeStreamEntry;
import bot.finance.common.fixtures.ChangeStreamHealth;
import bot.finance.common.fixtures.ExpensePatches;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.ExpenseStatus;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers {@code PATCH /api/v1/expenses/RECORDED/{id}} end to end against the fully wired application with capture
 * switched on, entered the way a browser does: signing in and carrying the session cookie and CSRF token a write
 * needs. Redis is reached through the Toxiproxy singleton rather than directly, so the unhappy path can cut and
 * restore the connection inside this one booted context.
 */
@CdcCaptureTest
class BroadcastLedgerChangesSystemTest {

    private static final String STREAM_KEY = CdcCaptureTest.STREAM_KEY;
    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

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
        @DisplayName("when a recorded expense's category is changed - then the stream names both categories")
        void whenARecordedExpensesCategoryIsChanged_thenTheStreamEntryNamesBothCategoriesAndGroupings() {
            String externalId = "broadcast-ledger-happy-user";
            String sessionCookie = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long firstCategoryId = groceriesCategoryId(userId, "Supermarkets");
            long secondCategoryId = diningCategoryId(userId, "Restaurants");
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
            String csrfToken = BrowserSessions.csrfToken();

            // when: the expense's category is changed through the endpoint
            Response response = patchCategory(sessionCookie, csrfToken, expenseId, secondCategoryId);
            response.then().statusCode(200);

            // then: one ExpenseRefiled entry reaches ledger.cdc carrying the expenseId
            await("the refile reaches the stream")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(refiledEntryFor(userId, expenseId))
                            .as("an ExpenseRefiled entry for the changed expense")
                            .isPresent());
            ChangeStreamEntry entry = refiledEntryFor(userId, expenseId).orElseThrow();

            // then: the payload names the new category and grouping, with their ids beside them
            assertThat(entry.payload().path("category").path("id").asLong())
                    .as("payload.category.id")
                    .isEqualTo(secondCategoryId);
            assertThat(entry.payload().path("category").path("name").asText())
                    .as("payload.category.name")
                    .isEqualTo("Restaurants");
            assertThat(entry.payload().path("grouping").path("name").asText())
                    .as("payload.grouping.name")
                    .isEqualTo("Dining");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when redis refuses then recovers - then nothing publishes until it returns")
        void whenRedisRefusesThenRecovers_thenNothingPublishesUntilItReturns() {
            String externalId = "broadcast-ledger-unhappy-user";
            String sessionCookie = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long firstCategoryId = groceriesCategoryId(userId, "Supermarkets");
            long secondCategoryId = diningCategoryId(userId, "Restaurants");
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
            String csrfToken = BrowserSessions.csrfToken();

            // given: the connection to Redis is cut at the proxy
            ToxiproxyContainers.REDIS_PROXY.setConnectionCut(true);
            try {
                // when: a category change is made while Redis refuses
                Response response = patchCategory(sessionCookie, csrfToken, expenseId, secondCategoryId);
                response.then().statusCode(200);

                // then: the health component reads DOWN while Redis refuses
                ChangeStreamHealth.awaitState(managementPort, "DOWN", TIMEOUT);

                // then: nothing is published while it refuses
                assertThat(refiledEntryFor(userId, expenseId))
                        .as("no ExpenseRefiled entry published while Redis refuses")
                        .isEmpty();
            } finally {
                // when: Redis becomes reachable afterwards
                ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
            }

            // then: the held change reaches the stream once Redis returns
            await("the held change reaches the stream once redis returns")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(refiledEntryFor(userId, expenseId))
                            .as("the held ExpenseRefiled entry eventually reaches the stream")
                            .isPresent());
        }
    }

    private Optional<ChangeStreamEntry> refiledEntryFor(long userId, long expenseId) {
        return ChangeStreamEntries.entriesOnFor(STREAM_KEY, "ExpenseRefiled", userId).stream()
                .filter(entry -> expenseId == entry.payload().path("expenseId").asLong())
                .findFirst();
    }

    private long groceriesCategoryId(long userId, String name) {
        return CategoryRowUtils.categoryIdUnderGrouping(jdbcAggregateTemplate, userId, "Groceries", name);
    }

    private long diningCategoryId(long userId, String name) {
        return CategoryRowUtils.categoryIdUnderGrouping(jdbcAggregateTemplate, userId, "Dining", name);
    }

    private Response patchCategory(String sessionCookie, String csrfToken, long expenseId, long categoryId) {
        return ExpensePatches.replaceCategory(sessionCookie, csrfToken, expenseId, categoryId);
    }
}
