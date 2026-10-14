package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.TelegramTestBot.recordedSendMessagesFor;
import static bot.finance.common.stubs.TelegramTestBot.replyParameters;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsSendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.IncomingTurnRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.model.User;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.Status;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers a turn that fails: the user is told nothing was noted, and the batch is confirmed anyway.
 */
class HandleIncomingMessageFailureSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.HANDLE_MESSAGE_FAILURE;
    private static final TelegramTestBot.TelegramScenario REFUSED_SCENARIO = TelegramTestBot.HANDLE_MESSAGE_REFUSED;

    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String EXPECTED_TEXT = "Something went wrong and nothing was noted — please try again.";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    /**
     * The order here is load-bearing: the poll loop is already running, so the {@code sendMessage} stub and the
     * catch-all must be registered before the update-bearing stub exists, or the loop consumes the update before
     * the response it triggers can be recorded, and the catch-all must come first so the loop never sees a bare
     * 404.
     */
    private void stubTelegramDelivering(TelegramTestBot.TelegramScenario scenario) {
        telegramAcceptsSendMessage(TOKEN);
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramDeliversOnce(
                TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(
                        scenario.updateId(), scenario.userId(), scenario.chatId(), MESSAGE_TEXT)));
    }

    private void assertOneFailedReportWithNoButtons(TelegramTestBot.TelegramScenario scenario) {
        await("exactly one sendMessage reporting the FAILED outcome is recorded")
                .atMost(TIMEOUT)
                .untilAsserted(() -> {
                    List<LoggedRequest> sent = recordedSendMessagesFor(TOKEN, scenario);
                    log.debug("Recorded sendMessage requests: {}", sent);

                    assertThat(sent).hasSize(1);
                    LoggedRequest sendMessageRequest = sent.get(0);
                    assertThat(sendMessageRequest.formParameter("chat_id").getValues())
                            .containsExactly(scenario.conversationId());
                    assertThat(sendMessageRequest.formParameter("text").getValues())
                            .containsExactly(EXPECTED_TEXT);
                    assertThat(sendMessageRequest.formParameter("reply_markup").isPresent())
                            .as("a FAILED report has nothing to resolve, so no reply_markup form param is sent")
                            .isFalse();

                    assertThat(replyParameters(sendMessageRequest)
                                    .get("message_id")
                                    .asText())
                            .isEqualTo(String.valueOf(TelegramFixtures.MESSAGE_ID));
                });
    }

    @Nested
    @DisplayName("unhappy path - the turn fails")
    class UnhappyPath {

        @Test
        @DisplayName("when the loop picks the update up - then one sendMessage reports nothing was noted, with no "
                + "buttons")
        void whenLoopPicksUpdateUp_thenFailureIsLoggedAndBatchIsStillConfirmed() {
            // given: the connector accepts the hand-over and ends the turn failed
            GrpcStubServer.endTurnWith(TurnEnd.TURN_END_FAILED);
            stubTelegramDelivering(SCENARIO);

            // then: a connector that never answered does not stall the loop
            await("a follow-up getUpdates confirms the batch").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedPollsWithOffset(TOKEN, SCENARIO.nextOffset()))
                    .as("follow-up getUpdates polls carrying offset=%s", SCENARIO.nextOffset())
                    .isNotEmpty());

            // then: the user is told nothing was noted, and gets no buttons for a report with nothing to resolve
            assertOneFailedReportWithNoButtons(SCENARIO);

            // then: a failed turn leaves nothing half-recorded behind it
            Optional<User> storedUser = userRepository.findByExternalId(SCENARIO.userExternalId());
            storedUser.ifPresent(user -> assertThat(ExpenseRowUtils.expenseRowsFor(
                            jdbcAggregateTemplate, user.id().orElseThrow(), ExpenseStatus.PENDING))
                    .as("PENDING expense rows for the conversation's user")
                    .isEmpty());
        }

        @Test
        @DisplayName("when the connector refuses the hand-over - then one report says nothing was noted and no turn "
                + "row is left")
        void whenConnectorRefusesHandOver_thenOneReportSaysNothingWasNotedAndNoTurnRowIsLeft() {
            // given: the connector fails the hand-over with INVALID_ARGUMENT
            GrpcStubServer.failExtractionWith(Status.INVALID_ARGUMENT.withDescription("request refused"));
            stubTelegramDelivering(REFUSED_SCENARIO);

            // when: the loop picks the update up
            // then: one report says nothing was noted, with no buttons
            assertOneFailedReportWithNoButtons(REFUSED_SCENARIO);

            // then: no turn row is left for the message
            User storedUser = userRepository
                    .findByExternalId(REFUSED_SCENARIO.userExternalId())
                    .orElseThrow(() -> new AssertionError("the turn stored no user for the refused message"));
            assertThat(IncomingTurnRowUtils.turnRowsFor(
                            jdbcAggregateTemplate, storedUser.id().orElseThrow()))
                    .as("turn rows kept for the sender")
                    .isEmpty();
        }
    }
}
