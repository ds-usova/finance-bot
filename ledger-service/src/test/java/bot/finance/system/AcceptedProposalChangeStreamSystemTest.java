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
import java.util.Optional;
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
        @DisplayName("when a pending proposal is accepted - then one update carries it from pending to recorded "
                + "under the same id")
        void whenAPendingProposalIsAccepted_thenOneUpdateCarriesItFromPendingToRecordedUnderTheSameId() {
            String externalId = "accepted-proposal-happy-user";
            String sessionCookie = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);
            long proposalId = ExpenseRowUtils.storedExpense(
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
            String csrfToken = BrowserSessions.csrfToken();

            // when: the proposal is accepted through the endpoint
            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(proposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);
            response.then().statusCode(200);

            // then: one update entry on expense reaches the stream, carrying the row from pending to recorded
            await("the proposal's update reaches the stream").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            updateEntry(userId, proposalId))
                    .as("the proposal's update entry")
                    .isPresent());
            List<ChangeStreamEntry> updates = ChangeStreamEntries.entriesOnFor(STREAM_KEY, "expense", userId).stream()
                    .filter(entry -> "u".equals(entry.op()))
                    .toList();
            assertThat(updates)
                    .as("exactly one expense update reaches the stream")
                    .hasSize(1);
            ChangeStreamEntry update = updates.get(0);

            // then: the same id carries both sides, before PENDING and after RECORDED
            assertThat(update.before().path("id").asLong())
                    .as("the update's before id")
                    .isEqualTo(proposalId);
            assertThat(update.after().path("id").asLong())
                    .as("the update's after id, matching before")
                    .isEqualTo(proposalId);
            assertThat(update.before().path("status").asText())
                    .as("the update's before status")
                    .isEqualTo("PENDING");
            assertThat(update.after().path("status").asText())
                    .as("the update's after status")
                    .isEqualTo("RECORDED");
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

            // then: no expense entry carries that id
            assertThat(expenseEntryNaming(unknownProposalId))
                    .as("no expense entry for an id that never named a row")
                    .isEmpty();
        }
    }

    private Optional<ChangeStreamEntry> updateEntry(long userId, long proposalId) {
        return ChangeStreamEntries.entriesOnFor(STREAM_KEY, "expense", userId).stream()
                .filter(entry -> "u".equals(entry.op())
                        && entry.before().path("id").asLong() == proposalId
                        && entry.after().path("id").asLong() == proposalId)
                .findFirst();
    }

    private Optional<ChangeStreamEntry> expenseEntryNaming(long id) {
        return ChangeStreamEntries.allEntriesOn(STREAM_KEY).stream()
                .filter(entry -> "expense".equals(entry.table()))
                .filter(entry -> entry.before().path("id").asLong() == id
                        || entry.after().path("id").asLong() == id)
                .findFirst();
    }
}
