package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedAnswerCallbackQueriesFor;
import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.metrics.MeterName;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Covers {@code GET /actuator/prometheus} end to end against the fully wired application: proving the pipeline's
 * three new counters — {@code ledger_turns_total}, {@code ledger_mcp_tool_calls_total} and
 * {@code ledger_proposals_resolved_total} — reach the scrape.
 */
class PipelineMetersSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.PIPELINE_METERS;

    private static final String MESSAGE_TEXT = "coffee 3 euro";

    private static final String PROPOSAL_CATEGORY = "Supermarkets";
    private static final String PROPOSAL_GROUPING = "Groceries";
    private static final String PROPOSAL_DESCRIPTION = "coffee";
    private static final String PROPOSAL_MERCHANT = "Cafe";
    private static final String PROPOSAL_AMOUNT_TEXT = "3.00";
    private static final String CURRENCY_CODE = "EUR";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    private Response callCreateExpenseProposal(String token, String requestBody) {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(McpRequests.ACCEPT_HEADER)
                .header("Authorization", "Bearer " + token)
                .body(requestBody)
                .when()
                .post("/mcp");
        logResponse(response);
        return response;
    }

    private Response scrapePrometheus() {
        Response response = RestAssured.given().port(managementPort).when().get("/actuator/prometheus");
        logResponse(response);
        return response;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        /**
         * The order below is load-bearing: the poll loop is already running, so the catch-all and response stubs
         * must be registered, and the extraction callback armed, before the update-bearing stub — or the loop
         * consumes the batch before the response it triggers can be recorded.
         */
        @Test
        @DisplayName("when prometheus is scraped after a confirmed turn - then it carries turn, tool call and "
                + "resolution counts")
        void whenPrometheusIsScrapedAfterAConfirmedTurn_thenItCarriesTurnToolCallAndResolutionCounts() {
            // given: a Telegram turn processed end to end, its report's Confirm button tapped
            WireMockStubs.telegramReturnsNoUpdates(TOKEN);
            WireMockStubs.telegramAcceptsSendMessage(TOKEN);
            WireMockStubs.telegramAcceptsAnswerCallbackQuery(TOKEN);
            WireMockStubs.telegramAcceptsEditMessageReplyMarkup(TOKEN);
            GrpcStubServer.armMcpCallbacks(
                    "http://localhost:" + port,
                    McpRequests.listCategories(PROPOSAL_GROUPING),
                    McpRequests.createExpenseProposal(
                            PROPOSAL_CATEGORY,
                            PROPOSAL_GROUPING,
                            PROPOSAL_DESCRIPTION,
                            PROPOSAL_MERCHANT,
                            PROPOSAL_AMOUNT_TEXT,
                            CURRENCY_CODE));

            // the incoming message id HandleIncomingMessageUseCase derives from this turn's own conversation and
            // message, so the accept tap below can name it without any use case seeding it first
            String reference = SCENARIO.conversationId() + ":" + TelegramFixtures.MESSAGE_ID;
            int callbackUpdateId = SCENARIO.updateId() + 1;
            String nextOffset = String.valueOf(callbackUpdateId + 1);

            WireMockStubs.telegramDeliversOnce(
                    TOKEN,
                    TelegramFixtures.updatesResponse(
                            TelegramFixtures.textMessageUpdate(
                                    SCENARIO.updateId(), SCENARIO.userId(), SCENARIO.chatId(), MESSAGE_TEXT),
                            TelegramFixtures.callbackQueryUpdate(
                                    callbackUpdateId,
                                    SCENARIO.callbackQueryId(),
                                    SCENARIO.userId(),
                                    SCENARIO.chatId(),
                                    TelegramFixtures.MESSAGE_ID,
                                    "accept:" + reference)));

            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + nextOffset)
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(TOKEN, nextOffset))
                            .as("follow-up getUpdates polls carrying offset=%s", nextOffset)
                            .isNotEmpty());
            await("the accept tap is answered").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedAnswerCallbackQueriesFor(TOKEN, SCENARIO))
                    .as("answerCallbackQuery requests recorded for token %s", TOKEN)
                    .isNotEmpty());

            // given: a second create_expense_proposal call posted to /mcp with a minted token
            long userId = userEntityRepository
                    .findByExternalId(SCENARIO.userExternalId())
                    .orElseThrow()
                    .id();
            String token = McpTokens.tokenFor(accessTokenMinter, userId);
            Response mcpResponse = callCreateExpenseProposal(
                    token,
                    McpRequests.createExpenseProposal(
                            PROPOSAL_CATEGORY,
                            PROPOSAL_GROUPING,
                            "milk",
                            PROPOSAL_MERCHANT,
                            PROPOSAL_AMOUNT_TEXT,
                            CURRENCY_CODE));
            assertThat(mcpResponse.statusCode())
                    .as("direct create_expense_proposal HTTP status")
                    .isEqualTo(200);
            assertThat(mcpResponse.jsonPath().getBoolean("result.isError"))
                    .as("direct create_expense_proposal tool result isError")
                    .isNotEqualTo(Boolean.TRUE);

            // when: the management port's /actuator/prometheus is scraped
            Response scrape = scrapePrometheus();
            scrape.then().statusCode(200);
            String body = scrape.getBody().asString();

            // then: the scrape carries a turn count, a successful tool call count and an accepted resolution count
            assertThat(body).as("turn count").contains(MeterName.TURNS.meterName());
            assertThat(body)
                    .as("successful tool call count")
                    .containsPattern(Pattern.compile(
                            "(?m)^" + MeterName.TOOL_CALLS.meterName() + "\\{(?=[^}]*outcome=\"ok\")[^}]*}"));
            assertThat(body)
                    .as("accepted resolution count")
                    .containsPattern(Pattern.compile("(?m)^" + MeterName.PROPOSALS_RESOLVED.meterName()
                            + "\\{(?=[^}]*resolution=\"accepted\")[^}]*}"));
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when the posted grouping matches nothing stored - then the scrape carries a rejected tool "
                + "call sample")
        void whenThePostedGroupingMatchesNothingStored_thenTheScrapeCarriesARejectedToolCallSample() {
            // given: a stored user with no grouping of the posted name
            User user = userRepository.create(User.newUser("pipeline-meters-unhappy-path-user"), Grouping.defaults());
            long userId = user.id().orElseThrow();
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            // when: create_expense_proposal is posted to /mcp naming a grouping this user has none of
            Response response = callCreateExpenseProposal(
                    token,
                    McpRequests.createExpenseProposal(
                            "Supermarkets", "NoSuchGrouping", "milk", "Corner Shop", "7.20", CURRENCY_CODE));
            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isTrue();

            // when: the management port is scraped
            Response scrape = scrapePrometheus();
            scrape.then().statusCode(200);
            String body = scrape.getBody().asString();

            // then: the scrape carries a rejected tool call sample naming the refusing exception as its reason
            assertThat(body)
                    .as("rejected tool call count, tagged with the refusing exception")
                    .containsPattern(Pattern.compile("(?m)^" + MeterName.TOOL_CALLS.meterName()
                            + "\\{(?=[^}]*outcome=\"rejected\")(?=[^}]*reason=\"InvalidGroupingException\")[^}]*}"));
        }
    }
}
