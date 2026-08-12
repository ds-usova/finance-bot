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
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Covers {@code PATCH /api/v1/expenses/RECORDED/{id}} end to end against the fully wired application with capture
 * switched on, entered the way a browser does: signing in and carrying the session cookie and CSRF token a write
 * needs. Redis is reached through the Toxiproxy singleton rather than directly, so the unhappy path can cut and
 * restore the connection inside this one booted context.
 */
@CdcCaptureTest
@TestPropertySource(
        properties = {"cdc.slot-name=broadcast_ledger_changes_slot", "cdc.stream-key=broadcast-ledger-changes.cdc"})
class BroadcastLedgerChangesSystemTest {

    private static final String STREAM_KEY = "broadcast-ledger-changes.cdc";
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
                            Instant.now())
                    .id();
            String csrfToken = BrowserSessions.csrfToken();

            // when: the expense's category is changed through the endpoint
            Response response = patchCategory(sessionCookie, csrfToken, expenseId, secondCategoryId);
            response.then().statusCode(200);

            // then: an entry reaches ledger.cdc with op:u, source.table:expense, and both category ids
            await("the category change reaches the stream").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            updateEntryFor(userId, expenseId))
                    .as("an update entry for the changed expense")
                    .isPresent());
            ChangeStreamEntry entry = updateEntryFor(userId, expenseId).orElseThrow();
            assertThat(entry.op()).as("op").isEqualTo("u");
            assertThat(entry.table()).as("source.table").isEqualTo("expense");
            assertThat(entry.before().path("category_id").asLong())
                    .as("before.category_id")
                    .isEqualTo(firstCategoryId);
            assertThat(entry.after().path("category_id").asLong())
                    .as("after.category_id")
                    .isEqualTo(secondCategoryId);

            // then: the enrichment names both categories and both groupings - read straight off the expense
            // event, with no category event needed first
            assertThat(entry.enrichment().path("before").path("categoryName").asText())
                    .as("enrichment.before.categoryName")
                    .isEqualTo("Supermarkets");
            assertThat(entry.enrichment().path("before").path("groupingName").asText())
                    .as("enrichment.before.groupingName")
                    .isEqualTo("Groceries");
            assertThat(entry.enrichment().path("after").path("categoryName").asText())
                    .as("enrichment.after.categoryName")
                    .isEqualTo("Restaurants");
            assertThat(entry.enrichment().path("after").path("groupingName").asText())
                    .as("enrichment.after.groupingName")
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
                            Instant.now())
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
                assertThat(updateEntryFor(userId, expenseId))
                        .as("no update entry published while Redis refuses")
                        .isEmpty();
            } finally {
                // when: Redis becomes reachable afterwards
                ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
            }

            // then: the held change reaches the stream once Redis returns
            await("the held change reaches the stream once redis returns")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(updateEntryFor(userId, expenseId))
                            .as("the held update eventually reaches the stream")
                            .isPresent());
        }
    }

    private Optional<ChangeStreamEntry> updateEntryFor(long userId, long expenseId) {
        List<ChangeStreamEntry> entries = ChangeStreamEntries.entriesOnFor(STREAM_KEY, "expense", userId);
        return entries.stream()
                .filter(entry ->
                        "u".equals(entry.op()) && entry.after().path("id").asLong() == expenseId)
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
