package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.domain.value.WrittenMessageStatus;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/** Covers listing the written messages a person has being read. */
class BrowseWrittenMessagesSystemTest extends AbstractSystemTest {

    private static final String BOT_TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;
    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;

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
        @DisplayName("when a person asks for their texts being read - then 200 with their one message alone")
        void whenAPersonAsksForTheirTextsBeingRead_then200WithTheirOneMessageAlone() {
            // given: a person signed in with a READING message, and another person with one too
            String externalId = "browse-written-messages-happy-user";
            String sessionCookie =
                    BrowserSessions.signIn(BOT_TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            long ownMessageId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "own text", WrittenMessageStatus.READING, null, Instant.now());
            long otherUserId = UserRowUtils.storedUserId(userEntityRepository, "browse-written-messages-other-user");
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    otherUserId,
                    "other text",
                    WrittenMessageStatus.READING,
                    null,
                    Instant.now());

            // when: they ask for their texts being read
            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .queryParam("state", "READING")
                    .when()
                    .get("/api/v1/messages");
            logResponse(response);

            // then: the list holds their one message
            response.then().statusCode(200);
            assertThat(response.jsonPath().getList("id", Long.class))
                    .as("ids of the listed messages")
                    .isEqualTo(List.of(ownMessageId));
            assertThat(response.jsonPath().getList("text", String.class))
                    .as("texts of the listed messages")
                    .containsExactly("own text");
            assertThat(response.jsonPath().getList("state", String.class))
                    .as("states of the listed messages")
                    .containsExactly("READING");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a person whose user row is deleted asks for their texts being read - then 404")
        void whenAPersonWhoseUserRowIsDeletedAsksForTheirTextsBeingRead_then404() {
            // given: a person signed in, whose user row is then deleted
            String externalId = "browse-written-messages-deleted-user";
            String sessionCookie =
                    BrowserSessions.signIn(BOT_TOKEN, externalId).getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();
            userEntityRepository.deleteById(userId);

            // when: they ask for their texts being read
            Response response = RestAssured.given()
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .queryParam("state", "READING")
                    .when()
                    .get("/api/v1/messages");
            logResponse(response);

            // then: the caller is unknown
            response.then().statusCode(404);
        }
    }
}
