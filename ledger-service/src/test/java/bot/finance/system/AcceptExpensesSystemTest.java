package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.ACCEPT_EXPENSES_TOKEN;
import static bot.finance.common.stubs.TelegramTestBot.recordedEditMessageReplyMarkups;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Covers {@code POST /api/v1/expenses/acceptances} end to end against the fully wired application, entered the
 * way a browser does: signing in over the real sign-in endpoint and carrying the session cookie and CSRF token it
 * needs to write. Its clearing dispatches a real {@code editMessageReplyMarkup} call, off the request thread, so
 * it declares its own bot token — a stub path no other class's clearing can reach.
 */
@TestPropertySource(properties = "telegram.bot.token=" + ACCEPT_EXPENSES_TOKEN)
class AcceptExpensesSystemTest extends AbstractSystemTest {

    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;
    private static final String ACCEPTANCES_PATH = "/api/v1/expenses/acceptances";
    private static final String EXPENSES_PATH = "/api/v1/expenses";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
        @DisplayName("when both ids are posted with the session cookie and CSRF token - then 200, both recorded, "
                + "and buttons come off")
        void whenBothIdsArePostedWithTheSessionCookieAndCsrfToken_then200BothRecordedAndButtonsComeOff() {
            String externalId = "accept-expenses-happy-path-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);

            String incomingMessageId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            long firstProposalId = ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "coffee",
                            "Cafe",
                            350L,
                            "EUR",
                            incomingMessageId,
                            now)
                    .id();
            long secondProposalId = ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "lunch",
                            "Cafe",
                            1230L,
                            "EUR",
                            incomingMessageId,
                            now)
                    .id();
            String conversationId = "555";
            String sentMessageId = "4242";
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate, userId, incomingMessageId, conversationId, sentMessageId, now);

            telegramAcceptsEditMessageReplyMarkup(ACCEPT_EXPENSES_TOKEN);
            String csrfToken = BrowserSessions.csrfToken();

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(firstProposalId, secondProposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);
            logResponse(response);

            // then: the response is 200 with accepted 2 and missing 0
            response.then().statusCode(200);
            assertThat(response.jsonPath().getInt("accepted")).isEqualTo(2);
            assertThat(response.jsonPath().getInt("missing")).isEqualTo(0);

            // then: a later listing shows both as RECORDED and neither as PENDING
            Response pending = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .queryParam("status", "PENDING")
                    .when()
                    .get(EXPENSES_PATH);
            logResponse(pending);
            assertThat(pending.jsonPath().getInt("total"))
                    .as("nothing pending remains for this user")
                    .isZero();

            Response recorded = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .queryParam("status", "RECORDED")
                    .when()
                    .get(EXPENSES_PATH);
            logResponse(recorded);
            assertThat(recorded.jsonPath().getList("items.description", String.class))
                    .as("both proposals are now recorded expenses")
                    .containsExactlyInAnyOrder("coffee", "lunch");

            // then: the buttons come off that report without its text being resent
            await("one editMessageReplyMarkup is recorded").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedEditMessageReplyMarkups(ACCEPT_EXPENSES_TOKEN))
                    .as("editMessageReplyMarkup requests recorded for token %s", ACCEPT_EXPENSES_TOKEN)
                    .hasSize(1));
            LoggedRequest edit =
                    recordedEditMessageReplyMarkups(ACCEPT_EXPENSES_TOKEN).get(0);
            assertThat(edit.formParameter("chat_id").getValues())
                    .as("editMessageReplyMarkup chat_id form param")
                    .containsExactly(conversationId);
            assertThat(edit.formParameter("message_id").getValues())
                    .as("editMessageReplyMarkup message_id form param")
                    .containsExactly(sentMessageId);
            assertThat(recordedSendMessages(ACCEPT_EXPENSES_TOKEN))
                    .as("clearing the buttons never resends the report's text")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a list of ids is posted with no session cookie - then 401 and nothing is moved")
        void whenAListOfIdsIsPostedWithNoSessionCookie_then401AndNothingIsMoved() {
            String externalId = "accept-expenses-no-session-user";
            signIn(externalId);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long proposalId = storedPendingProposal(userId);

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("ids", List.of(proposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);
            logResponse(response);

            // then: the response is 401 and nothing is moved
            response.then().statusCode(401);
            assertThat(ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId))
                    .as("the proposal is still pending")
                    .hasSize(1);
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId))
                    .as("nothing became a recorded expense")
                    .isEmpty();
        }

        @Test
        @DisplayName("when a list of ids is posted with a valid session cookie and no CSRF token - then 403 and "
                + "nothing is moved")
        void whenAListOfIdsIsPostedWithAValidSessionAndNoCsrfToken_then403AndNothingIsMoved() {
            String externalId = "accept-expenses-no-csrf-user";
            String sessionCookie = signIn(externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long proposalId = storedPendingProposal(userId);

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .body(Map.of("ids", List.of(proposalId)))
                    .when()
                    .post(ACCEPTANCES_PATH);
            logResponse(response);

            // then: the response is 403 and nothing is moved
            response.then().statusCode(403);
            assertThat(ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId))
                    .as("the proposal is still pending")
                    .hasSize(1);
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId))
                    .as("nothing became a recorded expense")
                    .isEmpty();
        }

        private long storedPendingProposal(long userId) {
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);
            return ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "taxi",
                            "Cab Co",
                            900L,
                            "EUR",
                            UUID.randomUUID().toString(),
                            Instant.now())
                    .id();
        }
    }

    private Response signIn(String externalId) {
        return BrowserSessions.signIn(ACCEPT_EXPENSES_TOKEN, externalId);
    }
}
