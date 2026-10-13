package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedEditMessageReplyMarkupsFor;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessagesFor;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.ExpenseStatus;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers {@code POST /api/v1/expenses/discards} end to end against the fully wired application, entered the way a
 * browser does: signing in over the real sign-in endpoint and carrying the session cookie and CSRF token it needs
 * to write.
 */
class DiscardExpensesSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.DISCARD_EXPENSES;

    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;
    private static final String DISCARDS_PATH = "/api/v1/expenses/discards";
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
        @DisplayName("when two ids are posted with the session cookie and CSRF token - then 200, both gone, "
                + "and buttons come off")
        void whenTwoIdsArePostedWithTheSessionCookieAndCsrfToken_then200BothGoneAndButtonsComeOff() {
            // given: a person signed in, two PENDING entries under one reported message, and a third under another
            String externalId = "discard-expenses-happy-path-user";
            String sessionCookie = BrowserSessions.signIn(TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);

            String incomingMessageId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            long firstId = storedPending(userId, categoryId, "coffee", incomingMessageId, now);
            long secondId = storedPending(userId, categoryId, "lunch", incomingMessageId, now);
            long thirdId = storedPending(
                    userId, categoryId, "groceries", UUID.randomUUID().toString(), now);
            String conversationId = SCENARIO.conversationId();
            String sentMessageId = "4343";
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate, userId, incomingMessageId, conversationId, sentMessageId, now);

            telegramAcceptsEditMessageReplyMarkup(TOKEN);
            String csrfToken = BrowserSessions.csrfToken();

            // when: they post the ids of the two with the session cookie and a CSRF token
            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(firstId, secondId)))
                    .when()
                    .post(DISCARDS_PATH);
            logResponse(response);

            // then: the response is 200 with discarded 2 and missing 0
            response.then().statusCode(200);
            assertThat(response.jsonPath().getInt("discarded")).isEqualTo(2);
            assertThat(response.jsonPath().getInt("missing")).isEqualTo(0);

            // then: the third entry is the only PENDING row left and none is RECORDED
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.PENDING))
                    .as("pending expenses for user %s", userId)
                    .extracting(ExpenseEntity::id)
                    .containsExactly(thirdId);
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.RECORDED))
                    .as("nothing became a recorded expense")
                    .isEmpty();

            // then: the buttons come off that report without its text being resent
            await("one editMessageReplyMarkup is recorded").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedEditMessageReplyMarkupsFor(TOKEN, SCENARIO))
                    .as("editMessageReplyMarkup requests recorded for token %s", TOKEN)
                    .hasSize(1));
            LoggedRequest edit =
                    recordedEditMessageReplyMarkupsFor(TOKEN, SCENARIO).get(0);
            assertThat(edit.formParameter("chat_id").getValues())
                    .as("editMessageReplyMarkup chat_id form param")
                    .containsExactly(conversationId);
            assertThat(edit.formParameter("message_id").getValues())
                    .as("editMessageReplyMarkup message_id form param")
                    .containsExactly(sentMessageId);
            assertThat(recordedSendMessagesFor(TOKEN, SCENARIO))
                    .as("clearing the buttons never resends the report's text")
                    .isEmpty();
        }

        private long storedPending(
                long userId, long categoryId, String description, String incomingMessageId, Instant now) {
            return ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            description,
                            "Cafe",
                            350L,
                            "EUR",
                            incomingMessageId,
                            now,
                            ExpenseStatus.PENDING)
                    .id();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a person whose user row is deleted posts a list of ids - then 404")
        void whenAPersonWhoseUserRowIsDeletedPostsAListOfIds_then404() {
            // given: a person signed in, whose user row is then deleted
            String externalId = "discard-expenses-deleted-user";
            String sessionCookie = BrowserSessions.signIn(TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            userEntityRepository.deleteById(userId);
            String csrfToken = BrowserSessions.csrfToken();

            // when: they post a list of ids with the session cookie and a CSRF token
            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(Map.of("ids", List.of(1L, 2L)))
                    .when()
                    .post(DISCARDS_PATH);
            logResponse(response);

            // then: the caller is unknown
            response.then().statusCode(404);
        }
    }
}
