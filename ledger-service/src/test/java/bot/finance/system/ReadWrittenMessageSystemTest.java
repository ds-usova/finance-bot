package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.application.dto.ReportOutcome;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.WrittenMessageStatus;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/** Covers asking for a written message by id. */
class ReadWrittenMessageSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;
    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;

    private static final String MESSAGE_TEXT = "coffee 3.40, taxi home 14";
    private static final Instant STORED_AT = Instant.parse("2026-03-14T10:15:00Z");

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
        @DisplayName("when a person asks for their DONE message - then 200 with its outcome, text and entry day")
        void whenAPersonAsksForTheirDoneMessage_then200WithItsOutcomeTextAndEntryDay() {
            // given: a person signed in, a DONE message and a pending entry under it
            String externalId = "read-written-message-happy-user";
            String sessionCookie = BrowserSessions.signIn(BOT_TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    userId,
                    MESSAGE_TEXT,
                    WrittenMessageStatus.DONE,
                    ReportOutcome.RECORDED,
                    STORED_AT);
            long groupingId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Reading");
            long categoryId = CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, "Coffee");
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "coffee",
                    "Cafe",
                    340L,
                    "EUR",
                    "web:" + messageId,
                    STORED_AT,
                    ExpenseStatus.PENDING);

            // when: they ask for the message by id
            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/messages/{id}", messageId);
            logResponse(response);

            // then: it is DONE, RECORDED, with its text and the day its entry was stored on
            response.then().statusCode(200);
            assertThat(response.jsonPath().getLong("id")).as("id").isEqualTo(messageId);
            assertThat(response.jsonPath().getString("state")).as("state").isEqualTo("DONE");
            assertThat(response.jsonPath().getString("outcome")).as("outcome").isEqualTo("RECORDED");
            assertThat(response.jsonPath().getString("text")).as("text").isEqualTo(MESSAGE_TEXT);
            assertThat(response.jsonPath().getList("days", String.class))
                    .as("days")
                    .containsExactly("2026-03-14");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a person asks for another person's message - then 404")
        void whenAPersonAsksForAnotherPersonsMessage_then404() {
            // given: a person signed in, and a message seeded for another person
            String sessionCookie = BrowserSessions.signIn(BOT_TOKEN, "read-written-message-asker-user")
                    .getCookie(SESSION_COOKIE);
            long otherUserId = UserRowUtils.storedUserId(userEntityRepository, "read-written-message-other-user");
            long messageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    otherUserId,
                    MESSAGE_TEXT,
                    WrittenMessageStatus.DONE,
                    ReportOutcome.RECORDED,
                    STORED_AT);

            // when: they ask for it by id
            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .when()
                    .get("/api/v1/messages/{id}", messageId);
            logResponse(response);

            // then: it is not found
            response.then().statusCode(404);
        }
    }
}
