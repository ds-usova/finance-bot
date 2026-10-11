package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.WrittenMessageStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/** Covers filing a written expense from the web, from the request to the entries the reader stores. */
class FileWrittenMessageSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;
    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;

    private static final String MESSAGE_TEXT = "coffee 3.40, taxi home 14";
    private static final String CATEGORY = "Supermarkets";
    private static final String GROUPING = "Groceries";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

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
        @DisplayName("when a person files two expenses in one text - then it is read to DONE with two pending entries")
        void whenAPersonFilesTwoExpensesInOneText_thenItIsReadToDoneWithTwoPendingEntries() {
            // given: a person signed in, and the connector armed to record one proposal per expense
            String externalId = "file-written-message-happy-user";
            String sessionCookie = BrowserSessions.signIn(BOT_TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            GrpcStubServer.armMcpCallbacks(
                    "http://localhost:" + port,
                    McpRequests.createExpenseProposal(CATEGORY, GROUPING, "coffee", "Cafe", "3.40", "EUR"),
                    McpRequests.createExpenseProposal(CATEGORY, GROUPING, "taxi home", "Taxi", "14", "EUR"));

            // when: they file the text
            String csrfToken = BrowserSessions.csrfToken();
            Response filed = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("text", MESSAGE_TEXT))
                    .when()
                    .post("/api/v1/messages");
            logResponse(filed);

            // then: it is accepted as being read
            filed.then().statusCode(202);
            assertThat(filed.jsonPath().getString("state"))
                    .as("state on filing")
                    .isEqualTo("READING");
            assertThat(filed.jsonPath().getString("text")).as("text on filing").isEqualTo(MESSAGE_TEXT);
            long messageId = filed.jsonPath().getLong("id");

            // then: asking for it by id comes to DONE, RECORDED, with today's UTC day
            await("the message is read to DONE")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(readState(sessionCookie, messageId))
                            .as("state of message %s", messageId)
                            .isEqualTo("DONE"));
            Response read = readMessage(sessionCookie, messageId);
            assertThat(read.jsonPath().getString("outcome")).as("outcome").isEqualTo("RECORDED");
            assertThat(read.jsonPath().getList("days", String.class))
                    .as("the days the entries were stored on")
                    .containsExactly(LocalDate.now(Clock.systemUTC()).toString());

            // then: two pending entries are stored under the message
            List<ExpenseEntity> entries =
                    ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.PENDING);
            assertThat(entries).as("PENDING entries of the person").hasSize(2).allSatisfy(entry -> assertThat(
                            entry.incomingMessageId())
                    .as("the message the entry is filed under")
                    .isEqualTo("web:" + messageId));

            // then: no proposal report is stored for them
            assertThat(ProposalReportRowUtils.proposalReportRowsFor(jdbcAggregateTemplate, userId))
                    .as("proposal reports of the person")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a person with a message being read files another - then 429 and still one message row")
        void whenAPersonWithAMessageBeingReadFilesAnother_then429AndStillOneMessageRow() {
            // given: a person signed in, with a READING message
            String externalId = "file-written-message-busy-user";
            String sessionCookie = BrowserSessions.signIn(BOT_TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "earlier text", WrittenMessageStatus.READING, null, Instant.now());

            // when: they file a text
            String csrfToken = BrowserSessions.csrfToken();
            Response filed = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("text", MESSAGE_TEXT))
                    .when()
                    .post("/api/v1/messages");
            logResponse(filed);

            // then: it is refused as too many
            filed.then().statusCode(429);

            // then: they still hold one message row
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM written_message WHERE user_id = ?", Integer.class, userId);
            assertThat(rows).as("message rows of the person").isEqualTo(1);
        }
    }

    private Response readMessage(String sessionCookie, long messageId) {
        return RestAssured.given().cookie(SESSION_COOKIE, sessionCookie).when().get("/api/v1/messages/{id}", messageId);
    }

    private String readState(String sessionCookie, long messageId) {
        return readMessage(sessionCookie, messageId).jsonPath().getString("state");
    }
}
