package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.REFILE_REPORTED_PROPOSAL_TOKEN;
import static bot.finance.common.stubs.TelegramTestBot.recordedBotApiMethods;
import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsAnswerCallbackQuery;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static bot.finance.common.stubs.WireMockStubs.telegramReturnsNoUpdates;
import static bot.finance.common.stubs.WireMockStubs.telegramReturnsOnFirstPoll;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import io.restassured.RestAssured;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Covers {@code TelegramUpdateListener.process()} end to end for a proposal that was reported to Telegram, then
 * refiled from the web, then confirmed by tapping the report. Its Telegram scenario reaches a stub path no other
 * class can, so it declares its own bot token and holds one triggered scenario and no more.
 */
@TestPropertySource(properties = "telegram.bot.token=" + REFILE_REPORTED_PROPOSAL_TOKEN)
class RefileReportedProposalSystemTest extends AbstractSystemTest {

    private static final long FROM_ID = 9001L;
    private static final String FROM_ID_STRING = String.valueOf(FROM_ID);
    private static final long CHAT_ID = 4242L;
    private static final int MESSAGE_ID = 555;
    private static final int UPDATE_ID = 42;
    private static final String NEXT_OFFSET = String.valueOf(UPDATE_ID + 1);
    private static final String EXPENSES_PATH = "/api/v1/expenses";
    private static final String PATCH_MEDIA_TYPE = "application/json-patch+json";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String SESSION_COOKIE = BrowserSessions.COOKIE_NAME;
    private static final String CSRF_COOKIE = BrowserSessions.CSRF_COOKIE;
    private static final String CSRF_HEADER = BrowserSessions.CSRF_HEADER;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @BeforeEach
    void configureRestAssuredAndTelegram() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        telegramReturnsNoUpdates(REFILE_REPORTED_PROPOSAL_TOKEN);
        telegramAcceptsAnswerCallbackQuery(REFILE_REPORTED_PROPOSAL_TOKEN);
        telegramAcceptsEditMessageReplyMarkup(REFILE_REPORTED_PROPOSAL_TOKEN);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a reported proposal is refiled then confirmed - then no message is sent and the new "
                + "category is recorded")
        void whenARefiledReportedProposalIsConfirmed_thenNothingIsSentForTheRefileAndTheNewCategoryIsRecorded() {
            String sessionCookie = BrowserSessions.signIn(REFILE_REPORTED_PROPOSAL_TOKEN, FROM_ID_STRING)
                    .getCookie(SESSION_COOKIE);
            long userId = userEntityRepository
                    .findByExternalId(FROM_ID_STRING)
                    .orElseThrow()
                    .id();

            long groupingId = CategoryRowUtils.categoryIdNamed(jdbcAggregateTemplate, userId, null, "Groceries");
            long oldCategoryId =
                    CategoryRowUtils.categoryIdNamed(jdbcAggregateTemplate, userId, groupingId, "Supermarkets");
            long newCategoryId = CategoryRowUtils.categoryIdNamed(jdbcAggregateTemplate, userId, groupingId, "Markets");

            String reference = UUID.randomUUID().toString();
            Instant now = Instant.now();
            long proposalId = ExpenseProposalRowUtils.storedProposal(
                            jdbcAggregateTemplate,
                            userId,
                            oldCategoryId,
                            "flowers",
                            "Market",
                            800L,
                            "EUR",
                            reference,
                            now)
                    .id();

            // given: the proposal was reported to Telegram under its old category, and that report's location
            // recorded
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate, userId, reference, String.valueOf(CHAT_ID), String.valueOf(MESSAGE_ID), now);

            String csrfToken = BrowserSessions.csrfToken();

            // when: the proposal is refiled from the web endpoint
            Response refileResponse = RestAssured.given()
                    .contentType(PATCH_MEDIA_TYPE)
                    .cookie(SESSION_COOKIE, sessionCookie)
                    .cookie(CSRF_COOKIE, csrfToken)
                    .header(CSRF_HEADER, csrfToken)
                    .body(List.of(Map.of("op", "replace", "path", "/categoryId", "value", newCategoryId)))
                    .when()
                    .patch("%s/PENDING/%d".formatted(EXPENSES_PATH, proposalId));
            logResponse(refileResponse);

            // then: nothing was sent to Telegram for the refile - no sendMessage and no editMessageText between
            // the report and the tap
            assertThat(recordedSendMessages(REFILE_REPORTED_PROPOSAL_TOKEN))
                    .as("no sendMessage recorded for the refile")
                    .isEmpty();
            assertThat(recordedBotApiMethods(REFILE_REPORTED_PROPOSAL_TOKEN))
                    .as("no editMessageText recorded for the refile")
                    .doesNotContain("editMessageText");

            // when: Confirm is then tapped on that report
            telegramReturnsOnFirstPoll(
                    REFILE_REPORTED_PROPOSAL_TOKEN,
                    TelegramFixtures.updatesResponse(TelegramFixtures.callbackQueryUpdate(
                            UPDATE_ID, FROM_ID, CHAT_ID, MESSAGE_ID, "accept:" + reference)));

            await("the tap is consumed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(TIMEOUT)
                    .untilAsserted(
                            () -> assertThat(recordedPollsWithOffset(REFILE_REPORTED_PROPOSAL_TOKEN, NEXT_OFFSET))
                                    .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                                    .isNotEmpty());

            // then: the expense the tap records carries the new category, not the one the report named
            List<ExpenseEntity> expenseRows = ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
            assertThat(expenseRows)
                    .as("the confirmed proposal is now one recorded expense")
                    .hasSize(1);
            assertThat(expenseRows.get(0).categoryId())
                    .as("the recorded expense carries the category it was refiled to, not the reported one")
                    .isEqualTo(newCategoryId);
        }
    }
}
