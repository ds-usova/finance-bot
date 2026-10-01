package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedPolls;
import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessagesFor;
import static bot.finance.common.stubs.TelegramTestBot.replyMarkup;
import static bot.finance.common.stubs.TelegramTestBot.replyParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.common.LogCapture;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserPreferenceRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Grouping;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Metadata;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

class ReceiveTelegramMessageSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.RECEIVE_MESSAGE;

    private static final String FROM_ID_STRING = SCENARIO.userExternalId();
    private static final String CHAT_ID_STRING = SCENARIO.conversationId();
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String NEXT_OFFSET = SCENARIO.nextOffset();

    private static final String PROPOSAL_CATEGORY = "Supermarkets";
    private static final String PROPOSAL_GROUPING = "Groceries";
    private static final String PROPOSAL_DESCRIPTION = "lunch";
    private static final String PROPOSAL_MERCHANT = "Cafe";
    private static final String PROPOSAL_CURRENCY_CODE = "EUR";
    private static final String PROPOSAL_AMOUNT_TEXT = "12.30";
    private static final String EXPECTED_REPORT_OPENING = "Noted 1 expense, pending your confirmation:";

    /** Every category {@link Grouping#defaults()} gives a new user: the groupings and their categories alike. */
    private static final int EXPECTED_CATEGORY_COUNT = Grouping.defaults().size()
            + Grouping.defaults().stream()
                    .mapToInt(grouping -> grouping.categories().size())
                    .sum();

    /** The grouping names {@link Grouping#defaults()} seeds, sorted the way the groupings travel. */
    private static final List<String> EXPECTED_GROUPING_NAMES =
            Grouping.defaults().stream().map(Grouping::name).sorted().toList();

    /** The categories {@link Grouping#defaults()} files under {@link #PROPOSAL_GROUPING}. */
    private static final List<String> EXPECTED_GROUPING_CATEGORIES = Grouping.defaults().stream()
            .filter(grouping -> PROPOSAL_GROUPING.equals(grouping.name()))
            .flatMap(grouping -> grouping.categories().stream())
            .map(bot.finance.domain.value.Category::name)
            .toList();

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private LogCapture logCapture;

    /**
     * The order below is load-bearing: the poll loop is already running, so the appender must be attached and the
     * {@code sendMessage}/catch-all stubs and the gRPC callback armed before the update-bearing stub exists, or
     * the loop consumes the update before the response it triggers can be recorded. The catch-all comes before the
     * update-bearing stub so the loop never sees a bare 404.
     *
     * <p>The update-bearing stub itself is armed by each test, once its own preconditions are ready — two
     * scenarios share this token's one poll loop, so only the test that is about to assert on its outcome may put
     * the delivery scenario into {@code STARTED}.
     */
    @BeforeEach
    void stubTelegram() {
        logCapture = LogCapture.attachedTo(HandleIncomingMessageUseCase.class);
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramAcceptsSendMessage(TOKEN);
        GrpcStubServer.armMcpCallbacks(
                "http://localhost:" + port,
                McpRequests.listCategories(PROPOSAL_GROUPING),
                McpRequests.createExpenseProposal(
                        PROPOSAL_CATEGORY,
                        PROPOSAL_GROUPING,
                        PROPOSAL_DESCRIPTION,
                        PROPOSAL_MERCHANT,
                        PROPOSAL_AMOUNT_TEXT,
                        PROPOSAL_CURRENCY_CODE));
    }

    @AfterEach
    void detachLogCapture() {
        logPollLoopState();
        logCapture.close();
    }

    private void logPollLoopState() {
        List<String> polls = recordedPolls(TOKEN).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();

        log.debug("getUpdates requests recorded for token {}: {}", TOKEN, polls);
        log.debug("messages captured from {}: {}", HandleIncomingMessageUseCase.class.getName(), logCapture.messages());
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the poll loop picks up a text message - then the turn records one proposal and reports it "
                + "back with its buttons")
        void whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()
                throws ParseException {
            WireMockStubs.telegramDeliversOnce(
                    TOKEN,
                    TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(
                            SCENARIO.updateId(), SCENARIO.userId(), SCENARIO.chatId(), MESSAGE_TEXT)));

            // then: the message is consumed and its batch confirmed
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(TOKEN, NEXT_OFFSET))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            // then: the turn reaches the AI connector
            await("the AI connector receives an extraction request")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(GrpcStubServer.lastExtractionRequest())
                            .as("last ExtractIntentsRequest received by the stub AI connector")
                            .isNotNull());

            // then: the sender is stored as the user, not the chat, and starts with a full catalogue
            assertThat(userRepository.findByExternalId(CHAT_ID_STRING))
                    .as("no user should be stored under the chat id %s", CHAT_ID_STRING)
                    .isEmpty();
            User storedUser = userRepository
                    .findByExternalId(FROM_ID_STRING)
                    .orElseThrow(() -> new AssertionError("the turn stored no user for the from id " + FROM_ID_STRING));
            Integer storedCategoryCount = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM category WHERE user_id = ?",
                    Integer.class,
                    storedUser.id().orElseThrow());
            assertThat(storedCategoryCount)
                    .as("the categories the first message created for this conversation")
                    .isEqualTo(EXPECTED_CATEGORY_COUNT);

            // then: the connector is given the text, the user's groupings and the catch-all
            ExtractIntentsRequest request = GrpcStubServer.lastExtractionRequest();
            assertThat(request.getText()).as("extraction request text").isEqualTo(MESSAGE_TEXT);

            assertThat(request.getCategoryGroupingsList())
                    .as("extraction request category groupings")
                    .containsExactlyElementsOf(EXPECTED_GROUPING_NAMES);
            assertThat(request.getCatchAllGrouping())
                    .as("extraction request catch-all grouping")
                    .isEqualTo(Grouping.catchAllName());
            assertThat(LocalDate.parse(request.getCurrentDate()))
                    .as("extraction request current_date")
                    .isEqualTo(LocalDate.now(Clock.systemUTC()));

            // then: it acts as that user, for this one message, on a credential this service signed
            Metadata metadata = GrpcStubServer.lastExtractionMetadata();
            assertThat(metadata)
                    .as("metadata received by the stub AI connector")
                    .isNotNull();
            String authorizationHeader = metadata.get(AUTHORIZATION_KEY);
            assertThat(authorizationHeader).as("authorization metadata").startsWith("Bearer ");
            String token = authorizationHeader.substring("Bearer ".length());
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getSubject())
                    .as("jwt sub claim")
                    .isEqualTo(String.valueOf(storedUser.id().orElseThrow()));
            String incomingMessageIdClaim = claims.getStringClaim(McpTokens.INCOMING_MESSAGE_ID_CLAIM);
            assertThat(incomingMessageIdClaim).as("jwt imi claim").isNotNull();

            // then: one proposal is stored, filed under the message that produced it
            List<ExpenseEntity> proposalRows = ExpenseRowUtils.expenseRowsFor(
                    jdbcAggregateTemplate, storedUser.id().orElseThrow(), ExpenseStatus.PENDING);
            assertThat(proposalRows)
                    .as(
                            "stored PENDING expense rows for user %s",
                            storedUser.id().orElseThrow())
                    .hasSize(1);
            ExpenseEntity proposalRow = proposalRows.get(0);
            assertThat(proposalRow.incomingMessageId())
                    .as("stored proposal's incoming message id matches the bearer token's imi claim")
                    .isEqualTo(incomingMessageIdClaim);

            // then: the model called the two MCP tools in order — the categories first, then the proposal
            List<String> mcpAnswers = GrpcStubServer.mcpCallbackResponses();
            assertThat(mcpAnswers)
                    .as("what /mcp answered the stub connector, call by call")
                    .hasSize(2);
            assertThat(mcpAnswers.get(0))
                    .as("the list_categories answer, given before the proposal was recorded")
                    .contains(PROPOSAL_GROUPING)
                    .contains(EXPECTED_GROUPING_CATEGORIES.toArray(String[]::new));
            assertThat(mcpAnswers.get(1))
                    .as("the create_expense_proposal answer")
                    .contains(PROPOSAL_CATEGORY);

            // then: one report goes back into the chat, threaded onto the message it answers
            await("a sendMessage reply is recorded for the confirmed batch")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedSendMessagesFor(TOKEN, SCENARIO))
                            .as("sendMessage requests recorded for token %s", TOKEN)
                            .isNotEmpty());

            List<LoggedRequest> sent = recordedSendMessagesFor(TOKEN, SCENARIO);
            assertThat(sent).as("exactly one sendMessage recorded").hasSize(1);
            LoggedRequest sendMessageRequest = sent.get(0);
            assertThat(sendMessageRequest.formParameter("chat_id").getValues())
                    .as("sendMessage chat_id form param")
                    .containsExactly(CHAT_ID_STRING);
            assertThat(sendMessageRequest.formParameter("text").getValues())
                    .as("sendMessage text form param")
                    .hasSize(1)
                    .first()
                    .satisfies(text -> assertThat((String) text)
                            .startsWith(EXPECTED_REPORT_OPENING)
                            .contains(PROPOSAL_CATEGORY, PROPOSAL_AMOUNT_TEXT));

            assertThat(replyParameters(sendMessageRequest).get("message_id").asText())
                    .as("sendMessage reply_parameters message_id")
                    .isEqualTo(String.valueOf(TelegramFixtures.MESSAGE_ID));

            // then: the report carries the two buttons that make it resolvable, naming this message
            assertThat(sendMessageRequest.formParameter("reply_markup").isPresent())
                    .as("sendMessage reply_markup form param is present")
                    .isTrue();
            JsonNode buttonRow =
                    replyMarkup(sendMessageRequest).get("inline_keyboard").get(0);
            assertThat(buttonRow)
                    .as("one row of buttons in the report's keyboard")
                    .hasSize(2);
            List<String> callbackDataValues = List.of(
                    buttonRow.get(0).get("callback_data").asText(),
                    buttonRow.get(1).get("callback_data").asText());
            assertThat(callbackDataValues)
                    .as("both buttons' callback_data carry the imi claim's incoming message id")
                    .allSatisfy(callbackData -> assertThat(callbackData).endsWith(incomingMessageIdClaim));
        }

        @Test
        @DisplayName("when the sender's stored preference holds EUR - then the extraction request carries EUR as "
                + "the default currency")
        void whenSendersStoredPreferenceHoldsEur_thenExtractionRequestCarriesEurAsDefaultCurrency() {
            TelegramTestBot.TelegramScenario scenario = TelegramTestBot.RECEIVE_MESSAGE_WITH_DEFAULT_CURRENCY;
            String externalId = scenario.userExternalId();

            // given: the person already exists, with a preference row holding EUR
            BrowserSessions.signIn(TOKEN, externalId);
            long userId = userRepository
                    .findByExternalId(externalId)
                    .orElseThrow(() -> new AssertionError("sign-in stored no user for external id " + externalId))
                    .id()
                    .orElseThrow();
            UserPreferenceRowUtils.storedPreference(jdbcAggregateTemplate, userId, "EUR");

            WireMockStubs.telegramDeliversOnce(
                    TOKEN,
                    TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(
                            scenario.updateId(), scenario.userId(), scenario.chatId(), MESSAGE_TEXT)));

            // when: they send a message the bot polls
            await("the AI connector receives an extraction request")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(GrpcStubServer.lastExtractionRequest())
                            .as("last ExtractIntentsRequest received by the stub AI connector")
                            .isNotNull());

            // then: the extraction request the AI connector stub recorded carries default_currency EUR
            ExtractIntentsRequest request = GrpcStubServer.lastExtractionRequest();
            assertThat(request.hasDefaultCurrency())
                    .as("extraction request carries a default_currency")
                    .isTrue();
            assertThat(request.getDefaultCurrency())
                    .as("extraction request default_currency")
                    .isEqualTo("EUR");
        }
    }
}
