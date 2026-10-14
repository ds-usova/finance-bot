package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessagesFor;
import static bot.finance.common.stubs.TelegramTestBot.replyParameters;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.metrics.MeterName;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.PrometheusScrapes;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.IncomingTurnRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Grouping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/** Covers the reader's schedule settling turns whose end never came, with no request involved. */
class SweepIncomingTurnsSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.SWEEP_INCOMING_TURN;

    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String PARTIAL_HEADER = "Something went wrong, so this may be incomplete.";
    private static final String FAILED_TEXT = "Something went wrong and nothing was noted — please try again.";
    private static final String TELEGRAM_CHANNEL_TAG = "channel=\"telegram\"";
    private static final String UNREPORTED_TAG = "outcome=\"UNREPORTED\"";
    private static final String PROPOSAL_DESCRIPTION = "sweep lunch";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @BeforeEach
    void stubTelegram() {
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
    }

    private long storedUser(String externalId) {
        return userRepository
                .create(User.newUser(externalId), Grouping.defaults())
                .id()
                .orElseThrow();
    }

    private double sweptTelegramTurns() {
        return PrometheusScrapes.value(managementPort, MeterName.TURNS_SWEPT.meterName(), TELEGRAM_CHANNEL_TAG);
    }

    private double unreportedTurns() {
        return PrometheusScrapes.value(managementPort, MeterName.TURNS.meterName(), UNREPORTED_TAG);
    }

    private LoggedRequest awaitedReport() {
        await("a sendMessage report is recorded for the conversation")
                .atMost(TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> assertThat(recordedSendMessagesFor(TOKEN, SCENARIO))
                        .as("sendMessage requests recorded for the conversation")
                        .hasSize(1));
        return recordedSendMessagesFor(TOKEN, SCENARIO).get(0);
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a handed over turn has a pending expense under it - then it is swept and reported partial")
        void whenHandedOverTurnHasAPendingExpense_thenChatGetsPartialReportAndTurnIsCountedSwept() {
            // given: a turn handed over an hour ago, and a pending expense filed under its message
            WireMockStubs.telegramAcceptsSendMessage(TOKEN);
            String inboundMessageId = "1";
            String reference = SCENARIO.conversationId() + ":" + inboundMessageId;
            long userId = storedUser(SCENARIO.userExternalId());
            long categoryId = CategoryRowUtils.firstLeafCategoryId(jdbcAggregateTemplate, userId);
            Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
            double sweptBefore = sweptTelegramTurns();
            long turnId = IncomingTurnRowUtils.storedTurnId(
                    jdbcAggregateTemplate,
                    userId,
                    SCENARIO.conversationId(),
                    inboundMessageId,
                    MESSAGE_TEXT,
                    anHourAgo,
                    anHourAgo,
                    anHourAgo);
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    PROPOSAL_DESCRIPTION,
                    null,
                    1200L,
                    "EUR",
                    reference,
                    Instant.now(),
                    ExpenseStatus.PENDING);

            // when: the reader's schedule comes round
            // then: the chat receives the partial report, threaded on the message
            LoggedRequest report = awaitedReport();
            assertThat(report.formParameter("text").getValues())
                    .as("sendMessage text form param")
                    .hasSize(1)
                    .first()
                    .satisfies(text ->
                            assertThat((String) text).startsWith(PARTIAL_HEADER).contains(PROPOSAL_DESCRIPTION));
            assertThat(replyParameters(report).get("message_id").asText())
                    .as("sendMessage reply_parameters message_id")
                    .isEqualTo(inboundMessageId);

            // then: the turn's row is gone
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, turnId))
                    .as("the turn's row")
                    .isEmpty();

            // then: the scrape's swept count under the telegram channel has grown by one
            await("the swept count grows by one")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(sweptTelegramTurns())
                            .as("%s under %s", MeterName.TURNS_SWEPT.meterName(), TELEGRAM_CHANNEL_TAG)
                            .isEqualTo(sweptBefore + 1));
        }

        @Test
        @DisplayName("when a queued turn was kept and attempted an hour ago with nothing under it - then the chat "
                + "gets the failed report")
        void whenQueuedTurnIsAnHourOldWithNothingUnderIt_thenChatGetsFailedReport() {
            // given: a turn queued and kept an hour ago, attempted an hour ago, with nothing under its message
            WireMockStubs.telegramAcceptsSendMessage(TOKEN);
            String inboundMessageId = "2";
            long userId = storedUser("sweep-incoming-turn-queued-user");
            Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
            long turnId = IncomingTurnRowUtils.storedTurnId(
                    jdbcAggregateTemplate,
                    userId,
                    SCENARIO.conversationId(),
                    inboundMessageId,
                    MESSAGE_TEXT,
                    anHourAgo,
                    anHourAgo,
                    null);

            // when: the schedule comes round
            // then: the chat receives the failed report, threaded on the message
            LoggedRequest report = awaitedReport();
            assertThat(report.formParameter("text").getValues())
                    .as("sendMessage text form param")
                    .containsExactly(FAILED_TEXT);
            assertThat(replyParameters(report).get("message_id").asText())
                    .as("sendMessage reply_parameters message_id")
                    .isEqualTo(inboundMessageId);

            // then: the turn's row is gone
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, turnId))
                    .as("the turn's row")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when Telegram refuses the report of a swept turn - then the row is gone and the turn is "
                + "counted unreported")
        void whenTelegramRefusesTheReport_thenRowIsGoneAndTurnIsCountedUnreported() {
            // given: Telegram refusing sendMessage, and a turn handed over an hour ago
            WireMockStubs.telegramFailsSendMessage(TOKEN, 400, "simulated sendMessage failure");
            long userId = storedUser("sweep-incoming-turn-unreported-user");
            Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
            double unreportedBefore = unreportedTurns();
            long turnId = IncomingTurnRowUtils.storedTurnId(
                    jdbcAggregateTemplate,
                    userId,
                    SCENARIO.conversationId(),
                    "3",
                    MESSAGE_TEXT,
                    anHourAgo,
                    anHourAgo,
                    anHourAgo);

            // when: the schedule comes round
            // then: the row is gone
            await("the turn's row is gone")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, turnId))
                            .as("the turn's row")
                            .isEmpty());

            // then: the unreported count has grown by one
            await("the unreported count grows by one")
                    .atMost(TIMEOUT)
                    .pollInterval(POLL_INTERVAL)
                    .untilAsserted(() -> assertThat(unreportedTurns())
                            .as("%s under %s", MeterName.TURNS.meterName(), UNREPORTED_TAG)
                            .isEqualTo(unreportedBefore + 1));
        }
    }
}
