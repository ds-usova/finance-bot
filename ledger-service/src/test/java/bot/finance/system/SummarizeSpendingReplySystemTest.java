package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessagesFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.CategoryEntity;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Grouping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers a spending question arriving over the poll loop and its answer going back into the chat, end to end
 * against the fully wired application.
 */
class SummarizeSpendingReplySystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.SUMMARIZE_SPENDING;

    private static final String FROM_ID_STRING = SCENARIO.userExternalId();
    private static final String CHAT_ID_STRING = SCENARIO.conversationId();
    private static final String MESSAGE_TEXT = "how much did I spend last month";
    private static final String NEXT_OFFSET = SCENARIO.nextOffset();

    private static final String PERIOD_FROM = "2026-07-01";
    private static final String PERIOD_TO = "2026-07-31";

    private static final String EUR_DESCRIPTION = "in-period EUR expense";
    private static final long EUR_AMOUNT_MINOR_UNITS = 1500L; // 15.00 EUR
    private static final String USD_DESCRIPTION = "in-period USD expense";
    private static final long USD_AMOUNT_MINOR_UNITS = 2500L; // 25.00 USD
    private static final String OUTSIDE_DESCRIPTION = "outside-period EUR expense";
    private static final long OUTSIDE_AMOUNT_MINOR_UNITS = 9999L; // 99.99 EUR, dated outside the period

    private static final Instant INSIDE_PERIOD_INSTANT = Instant.parse("2026-07-15T12:00:00Z");
    private static final Instant OUTSIDE_PERIOD_INSTANT = Instant.parse("2026-06-15T12:00:00Z");

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    /**
     * The order below is load-bearing, the same way {@code ReceiveTelegramMessageSystemTest} explains it: the user
     * and the expenses are seeded first, since the poll loop is already running and must find them the moment it
     * picks up the message; the catch-all and callback-arming stubs are registered before the update-bearing one,
     * or the loop consumes the update before the response it triggers can be recorded.
     */
    @BeforeEach
    void seedUserAndExpensesThenStubTelegram() {
        User user = userRepository.create(User.newUser(FROM_ID_STRING), Grouping.defaults());
        long userId = user.id().orElseThrow();
        long categoryId = CategoryRowUtils.categoryRowsFor(jdbcAggregateTemplate, userId).stream()
                .filter(row -> row.parentId() != null)
                .map(CategoryEntity::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no seeded category for user " + userId));

        ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                EUR_DESCRIPTION,
                null,
                EUR_AMOUNT_MINOR_UNITS,
                "EUR",
                null,
                INSIDE_PERIOD_INSTANT,
                ExpenseStatus.RECORDED);
        ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                USD_DESCRIPTION,
                null,
                USD_AMOUNT_MINOR_UNITS,
                "USD",
                null,
                INSIDE_PERIOD_INSTANT,
                ExpenseStatus.RECORDED);
        ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                OUTSIDE_DESCRIPTION,
                null,
                OUTSIDE_AMOUNT_MINOR_UNITS,
                "EUR",
                null,
                OUTSIDE_PERIOD_INSTANT,
                ExpenseStatus.RECORDED);

        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramAcceptsSendMessage(TOKEN);
        GrpcStubServer.armMcpCallbacks(
                "http://localhost:" + port, McpRequests.summarizeSpending(PERIOD_FROM, PERIOD_TO));
        WireMockStubs.telegramDeliversOnce(
                TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(
                        SCENARIO.updateId(), SCENARIO.userId(), SCENARIO.chatId(), MESSAGE_TEXT)));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the poll loop picks up a text message asking what was spent - then the reply reports "
                + "the in-period totals")
        void whenPollLoopPicksUpSpendingQuestion_thenReplyReportsTheInPeriodTotals() {
            // then: the message is consumed and its batch confirmed
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(TOKEN, NEXT_OFFSET))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            // then: the extraction request reached the connector, carrying today's date
            await("the AI connector receives an extraction request")
                    .atMost(POLL_TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(GrpcStubServer.lastExtractionRequest())
                            .as("last ExtractIntentsRequest received by the stub AI connector")
                            .isNotNull());
            ExtractIntentsRequest request = GrpcStubServer.lastExtractionRequest();
            assertThat(LocalDate.parse(request.getCurrentDate()))
                    .as("extraction request current_date")
                    .isEqualTo(LocalDate.now(Clock.systemUTC()));

            // then: the model's summarize_spending call reached /mcp and was accepted for the seeded period
            List<String> mcpAnswers = GrpcStubServer.mcpCallbackResponses();
            assertThat(mcpAnswers)
                    .as("what /mcp answered the stub connector, call by call")
                    .hasSize(1);
            assertThat(mcpAnswers.get(0))
                    .as("the summarize_spending answer echoes the accepted period")
                    .contains(PERIOD_FROM)
                    .contains(PERIOD_TO);

            // then: one report goes back into the chat, threaded onto the message it answers
            await("a sendMessage reply is recorded for the answered turn")
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

            // then: the reply carries one line per currency with only the in-period totals
            String replyText =
                    sendMessageRequest.formParameter("text").getValues().get(0);
            assertThat(replyText)
                    .as("reply text carries the in-period EUR and USD totals, and no amount from outside the period")
                    .contains("15.00 EUR")
                    .contains("25.00 USD")
                    .doesNotContain("99.99");

            // then: no button markup is offered, since the turn produced no proposal
            assertThat(sendMessageRequest.formParameter("reply_markup").isPresent())
                    .as("sendMessage reply_markup form param is present")
                    .isFalse();
        }
    }
}
