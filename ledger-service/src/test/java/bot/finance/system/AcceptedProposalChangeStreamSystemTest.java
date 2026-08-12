package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamEntries.ChangeStreamEntry;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Covers {@code POST /api/v1/expenses/acceptances} end to end against the fully wired application with capture
 * switched on, entered the way a browser does: signing in and carrying the session cookie and CSRF token a write
 * needs.
 */
@CdcCaptureTest
@TestPropertySource(
        properties = {
            "cdc.slot-name=accepted_proposal_change_stream_slot",
            "cdc.stream-key=accepted-proposal-change-stream.cdc"
        })
class AcceptedProposalChangeStreamSystemTest {

    private static final String STREAM_KEY = "accepted-proposal-change-stream.cdc";
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
        @DisplayName("when a pending proposal is accepted - then the delete and the insert share one txId")
        void whenAPendingProposalIsAccepted_thenTheDeleteAndTheInsertShareOneTransactionId() {
            String externalId = "accepted-proposal-happy-user";
            String sessionCookie = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);
            long proposalId = ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "coffee",
                            "Cafe",
                            350L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now())
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

            // then: a d entry on expense_proposal and a c entry on expense reach the stream
            await("the proposal's delete and the expense's insert both reach the stream")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> {
                        assertThat(deleteEntry(userId, proposalId))
                                .as("the proposal's delete entry")
                                .isPresent();
                        assertThat(insertEntry(userId))
                                .as("the expense's insert entry")
                                .isPresent();
                    });
            ChangeStreamEntry delete = deleteEntry(userId, proposalId).orElseThrow();
            ChangeStreamEntry insert = insertEntry(userId).orElseThrow();

            // then: the two share one source.txId
            assertThat(insert.payload().path("source").path("txId").asLong())
                    .as("the expense insert's txId")
                    .isEqualTo(delete.payload().path("source").path("txId").asLong());

            // then: before is the whole deleted row, and after matches it
            assertThat(delete.before().path("description").asText()).isEqualTo("coffee");
            assertThat(insert.after().path("description").asText())
                    .as("the recorded expense's description matches the deleted proposal's")
                    .isEqualTo(delete.before().path("description").asText());
            assertThat(insert.after().path("amount_minor_units").asLong())
                    .as("the recorded expense's amount matches the deleted proposal's")
                    .isEqualTo(delete.before().path("amount_minor_units").asLong());
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

            // then: no entry for that id reaches the stream
            assertThat(deleteEntry(userId, unknownProposalId))
                    .as("no delete entry for an id that never named a row")
                    .isEmpty();
        }
    }

    private Optional<ChangeStreamEntry> deleteEntry(long userId, long proposalId) {
        return ChangeStreamEntries.entriesOnFor(STREAM_KEY, "expense_proposal", userId).stream()
                .filter(entry ->
                        "d".equals(entry.op()) && entry.before().path("id").asLong() == proposalId)
                .findFirst();
    }

    private Optional<ChangeStreamEntry> insertEntry(long userId) {
        return ChangeStreamEntries.entriesOnFor(STREAM_KEY, "expense", userId).stream()
                .filter(entry -> "c".equals(entry.op()))
                .findFirst();
    }
}
