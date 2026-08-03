package bot.finance.system;

import static bot.finance.common.TelegramTestBot.recordedPolls;
import static bot.finance.common.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.TelegramTestBot.replyParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseProposalEntity;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.KnownCategory;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.ExpenseProposalRowUtils;
import bot.finance.common.LogCapture;
import bot.finance.common.McpRequests;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.TelegramTestBot;
import bot.finance.common.WireMockStubs;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.grpc.Metadata;
import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The bot token below is what isolates this class: a differing property defeats Spring's context cache, so the
 * class gets its own context, a poll loop starting at offset 0, and a {@code /bot<token>/getUpdates} path no
 * other class's poller reaches.
 */
@TestPropertySource(properties = "telegram.bot.token=" + TelegramTestBot.RECEIVE_MESSAGE_TOKEN)
class ReceiveTelegramMessageSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.RECEIVE_MESSAGE_TOKEN;

    private static final int UPDATE_ID = 42;
    private static final long FROM_ID = 777L;
    private static final long CHAT_ID = 555L;
    private static final String FROM_ID_STRING = String.valueOf(FROM_ID);
    private static final String CHAT_ID_STRING = String.valueOf(CHAT_ID);
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String NEXT_OFFSET = "43";
    private static final String MESSAGE_REFERENCE_CLAIM = "mrf";

    private static final String PROPOSAL_CATEGORY = "Supermarkets";
    private static final String PROPOSAL_DESCRIPTION = "lunch";
    private static final String PROPOSAL_MERCHANT = "Cafe";
    private static final long PROPOSAL_AMOUNT_MINOR_UNITS = 1230L;
    private static final String PROPOSAL_CURRENCY_CODE = "EUR";
    private static final String PROPOSAL_AMOUNT_TEXT = "12.30";
    private static final String EXPECTED_REPORT_OPENING = "Noted 1 expense, pending your confirmation:";

    /** Every category {@link Category#defaults()} gives a new user: the groupings and their children alike. */
    private static final int EXPECTED_CATEGORY_COUNT = Category.defaults().size()
            + Category.defaults().stream()
                    .mapToInt(group -> group.children().size())
                    .sum();

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
     */
    @BeforeEach
    void stubTelegram() {
        logCapture = LogCapture.attachedTo(HandleIncomingMessageUseCase.class);
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramAcceptsSendMessage(TOKEN);
        GrpcStubServer.armMcpCallback(
                "http://localhost:" + port,
                McpRequests.createExpenseProposal(
                        PROPOSAL_CATEGORY,
                        null,
                        PROPOSAL_DESCRIPTION,
                        PROPOSAL_MERCHANT,
                        PROPOSAL_AMOUNT_MINOR_UNITS,
                        PROPOSAL_CURRENCY_CODE));
        WireMockStubs.telegramReturnsOnFirstPoll(
                TOKEN,
                TelegramFixtures.updatesResponse(
                        TelegramFixtures.textMessageUpdate(UPDATE_ID, FROM_ID, CHAT_ID, MESSAGE_TEXT)));
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
        @DisplayName("when the running poll loop picks up a text message update - then the batch is confirmed, the AI "
                + "connector receives the message text, the user's known categories with their parent "
                + "names, and a bearer token whose sub is the from id; a user is stored under the from "
                + "id rather than the chat id; one expense_proposal row is stored under the reference "
                + "the bearer token's mrf claim carries; and one sendMessage reply names the recorded "
                + "proposal")
        void whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()
                throws ParseException {
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(TOKEN, NEXT_OFFSET))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            await("the AI connector receives an extraction request")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(GrpcStubServer.lastExtractionRequest())
                            .as("last ExtractIntentsRequest received by the stub AI connector")
                            .isNotNull());

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

            ExtractIntentsRequest request = GrpcStubServer.lastExtractionRequest();
            assertThat(request.getText()).as("extraction request text").isEqualTo(MESSAGE_TEXT);

            List<KnownCategory> expectedKnownCategories = Category.defaults().stream()
                    .flatMap(group -> group.children().stream().map(child -> KnownCategory.newBuilder()
                            .setName(child.name())
                            .setParentName(group.name())
                            .build()))
                    .toList();
            assertThat(request.getKnownCategoriesList())
                    .as("extraction request's known categories")
                    .containsExactlyInAnyOrderElementsOf(expectedKnownCategories);

            Metadata metadata = GrpcStubServer.lastExtractionMetadata();
            assertThat(metadata)
                    .as("metadata received by the stub AI connector")
                    .isNotNull();
            String authorizationHeader = metadata.get(AUTHORIZATION_KEY);
            assertThat(authorizationHeader).as("authorization metadata").startsWith("Bearer ");
            String token = authorizationHeader.substring("Bearer ".length());
            JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
            assertThat(claims.getSubject()).as("jwt sub claim").isEqualTo(FROM_ID_STRING);
            String messageReferenceClaim = claims.getStringClaim(MESSAGE_REFERENCE_CLAIM);
            assertThat(messageReferenceClaim).as("jwt mrf claim").isNotNull();

            List<ExpenseProposalEntity> proposalRows = ExpenseProposalRowUtils.expenseProposalRowsFor(
                    jdbcAggregateTemplate, storedUser.id().orElseThrow());
            assertThat(proposalRows)
                    .as(
                            "stored expense_proposal rows for user %s",
                            storedUser.id().orElseThrow())
                    .hasSize(1);
            ExpenseProposalEntity proposalRow = proposalRows.get(0);
            assertThat(proposalRow.messageReference())
                    .as("stored proposal's message reference matches the bearer token's mrf claim")
                    .isEqualTo(UUID.fromString(messageReferenceClaim));

            await("a sendMessage reply is recorded for the confirmed batch")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedSendMessages(TOKEN))
                            .as("sendMessage requests recorded for token %s", TOKEN)
                            .isNotEmpty());

            List<LoggedRequest> sent = recordedSendMessages(TOKEN);
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
        }
    }
}
