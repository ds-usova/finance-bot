package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamEntries.ChangeStreamEntry;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.ExpenseStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers {@code POST /api/v1/expenses/acceptances} end to end against the fully wired application with capture
 * switched on, entered the way a browser does: signing in and carrying the session cookie and CSRF token a write
 * needs.
 */
@CdcCaptureTest
class AcceptedProposalChangeStreamSystemTest {

    private static final String STREAM_KEY = CdcCaptureTest.STREAM_KEY;
    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;
    private static final String ACCEPTANCES_PATH = "/api/v1/expenses/acceptances";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

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
        @DisplayName("when two proposals on two messages are accepted - then two ProposalAccepted entries reach the stream")
        void whenTwoProposalsAcceptedById_thenTwoProposalAcceptedEntriesReachStream() {
            String externalId = "accepted-proposal-happy-user";
            String sessionCookie = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);
            long firstProposalId = ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "coffee",
                            "Cafe",
                            350L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now(),
                            ExpenseStatus.PENDING)
                    .id();
            long secondProposalId = ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "lunch",
                            "Cafe",
                            1230L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now(),
                            ExpenseStatus.PENDING)
                    .id();
            String csrfToken = BrowserSessions.csrfToken();

            // when: both proposals are accepted by id through the endpoint
            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(firstProposalId, secondProposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);
            response.then().statusCode(200);
            assertThat(response.jsonPath().getInt("accepted"))
                    .as("both proposals reported on their own message are accepted")
                    .isEqualTo(2);

            // then: two ProposalAccepted entries reach the stream, keeping their pending expenseId
            await("both acceptances reach the stream")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(
                                    ChangeStreamEntries.entriesOnFor(STREAM_KEY, "ProposalAccepted", userId))
                            .as("ProposalAccepted entries for this user")
                            .hasSize(2));
            List<ChangeStreamEntry> accepted = ChangeStreamEntries.entriesOnFor(STREAM_KEY, "ProposalAccepted", userId);
            assertThat(accepted)
                    .extracting(entry -> entry.payload().path("expenseId").asLong())
                    .as("each entry keeps the expenseId its pending entry already had")
                    .containsExactlyInAnyOrder(firstProposalId, secondProposalId);

            // then: each entry carries status RECORDED, the pending-to-recorded fact
            assertThat(accepted)
                    .extracting(entry -> entry.payload().path("status").asText())
                    .as("both entries now read RECORDED")
                    .containsExactly("RECORDED", "RECORDED");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when an id names no proposal of theirs - then the endpoint answers as today and nothing "
                + "reaches the stream")
        void whenAnIdNamesNoProposalOfTheirs_thenEndpointAnswersAsTodayAndNothingReachesTheStream() {
            String externalId = "accepted-proposal-unhappy-user";
            String sessionCookie = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long unknownProposalId = 999_999L;
            String csrfToken = BrowserSessions.csrfToken();

            // when: an id naming no proposal of the caller's is posted
            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(unknownProposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);

            // then: the endpoint answers as it does today - accepted zero, missing one
            response.then().statusCode(200);
            assertThat(response.jsonPath().getInt("accepted"))
                    .as("nothing was accepted")
                    .isZero();
            assertThat(response.jsonPath().getInt("missing"))
                    .as("the unknown id is missing")
                    .isEqualTo(1);

            // then: no entry of any type carries that expenseId
            assertThat(ChangeStreamEntries.allEntriesOn(STREAM_KEY))
                    .as("no entry, of any type, carries an id that never named a row")
                    .noneMatch(entry -> userId == entry.userId()
                            && unknownProposalId == entry.payload().path("expenseId").asLong());
        }
    }
}
