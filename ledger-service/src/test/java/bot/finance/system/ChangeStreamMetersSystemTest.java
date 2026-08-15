package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.containers.ToxiproxyContainers;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamHealth;
import bot.finance.common.fixtures.ExpensePatches;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers {@code GET /actuator/prometheus} end to end against the fully wired application with capture switched on.
 * Redis is reached through the Toxiproxy singleton, so the happy path can produce both a published event and a
 * refused one inside this one booted context.
 */
@CdcCaptureTest
class ChangeStreamMetersSystemTest {

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
        @DisplayName(
                "when prometheus is scraped - then it carries the published, failure, lag, slot and state " + "meters")
        void whenPrometheusIsScraped_thenItCarriesThePublishedFailureLagSlotAndStateMeters() {
            String externalId = "change-stream-meters-user";
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

            // given: the engine has published at least one event
            patchCategory(sessionCookie, csrfToken, expenseId, secondCategoryId)
                    .then()
                    .statusCode(200);
            await("the first category change reaches the stream")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(ChangeStreamEntries.entriesOnFor(STREAM_KEY, "expense", userId))
                            .isNotEmpty());

            // given: Redis has refused at least one write, cut at the proxy
            ToxiproxyContainers.REDIS_PROXY.setConnectionCut(true);
            try {
                patchCategory(sessionCookie, csrfToken, expenseId, firstCategoryId)
                        .then()
                        .statusCode(200);
                ChangeStreamHealth.awaitState(managementPort, "DOWN", TIMEOUT);
            } finally {
                ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
            }

            // when: /actuator/prometheus is scraped on the management port
            Response response = RestAssured.given().port(managementPort).when().get("/actuator/prometheus");
            response.then().statusCode(200);
            String body = response.getBody().asString();

            // then: it carries a published count tagged by table and op
            assertThat(body)
                    .as("published count tagged by table and op")
                    .containsPattern(Pattern.compile("(?m)^ledger_cdc_events_published_total\\{"
                            + "(?=[^}]*table=\"expense\")(?=[^}]*op=\"u\")[^}]*}"));
            // then: a failure count
            assertThat(body).as("publish failure count").contains("ledger_cdc_publish_failures_total");
            // then: the event lag
            assertThat(body).as("event lag").contains("ledger_cdc_event_lag_seconds");
            // then: the slot's retained bytes and wal status
            assertThat(body).as("slot retained bytes").contains("ledger_cdc_slot_retained_bytes");
            assertThat(body).as("slot wal status").contains("ledger_cdc_slot_wal_status");
            // then: the engine's state
            assertThat(body).as("engine state").contains("ledger_cdc_state");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when prometheus is requested on the service port - then it is not served there")
        void whenPrometheusIsRequestedOnTheServicePort_thenItIsNotServedThere() {
            Response response = RestAssured.given().when().get("/actuator/prometheus");

            assertThat(response.statusCode())
                    .as("prometheus has moved off the service port")
                    .isNotEqualTo(200);
        }
    }

    private Response patchCategory(String sessionCookie, String csrfToken, long expenseId, long categoryId) {
        return ExpensePatches.replaceCategory(sessionCookie, csrfToken, expenseId, categoryId);
    }

    private long groceriesCategoryId(long userId, String name) {
        return CategoryRowUtils.categoryIdUnderGrouping(jdbcAggregateTemplate, userId, "Groceries", name);
    }

    private long diningCategoryId(long userId, String name) {
        return CategoryRowUtils.categoryIdUnderGrouping(jdbcAggregateTemplate, userId, "Dining", name);
    }
}
