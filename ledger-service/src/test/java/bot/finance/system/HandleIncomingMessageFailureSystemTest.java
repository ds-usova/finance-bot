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
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import bot.finance.common.stubs.WireMockStubs;
import bot.finance.domain.model.User;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.grpc.Status;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers a turn whose extraction fails: the user is told nothing was noted, and the batch is confirmed anyway.
 */
class HandleIncomingMessageFailureSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.HANDLE_MESSAGE_FAILURE;

    private static final String CONVERSATION_ID = SCENARIO.conversationId();
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String NEXT_OFFSET = SCENARIO.nextOffset();
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
    @BeforeEach
    void stubTelegramAndConnector() {
        telegramAcceptsSendMessage(TOKEN);
        WireMockStubs.telegramReturnsNoUpdates(TOKEN);
        WireMockStubs.telegramDeliversOnce(
                TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(
                        SCENARIO.updateId(), SCENARIO.userId(), SCENARIO.chatId(), MESSAGE_TEXT)));
        GrpcStubServer.failExtractionWith(Status.UNAVAILABLE.withDescription("AI connector unavailable"));
    }

    @Nested
    @DisplayName("unhappy path - the AI connector fails to extract intents")
    class UnhappyPath {

        @Test
        @DisplayName("when the loop picks the update up - then one sendMessage reports nothing was noted, with no "
                + "buttons")
        void whenLoopPicksUpdateUp_thenFailureIsLoggedAndBatchIsStillConfirmed() {
            // then: a connector that never answered does not stall the loop
            await("a follow-up getUpdates confirms the batch").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedPollsWithOffset(TOKEN, NEXT_OFFSET))
                    .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                    .isNotEmpty());

            // then: the user is told nothing was noted, and gets no buttons for a report with nothing to resolve
            await("exactly one sendMessage reporting the FAILED outcome is recorded")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> {
                        List<LoggedRequest> sent = recordedSendMessagesFor(TOKEN, SCENARIO);
                        log.debug("Recorded sendMessage requests: {}", sent);

                        assertThat(sent).hasSize(1);
                        LoggedRequest sendMessageRequest = sent.get(0);
                        assertThat(sendMessageRequest.formParameter("chat_id").getValues())
                                .containsExactly(CONVERSATION_ID);
                        assertThat(sendMessageRequest.formParameter("text").getValues())
                                .containsExactly(EXPECTED_TEXT);
                        assertThat(sendMessageRequest
                                        .formParameter("reply_markup")
                                        .isPresent())
                                .as("a FAILED report has nothing to resolve, so no reply_markup form param is sent")
                                .isFalse();

                        assertThat(replyParameters(sendMessageRequest)
                                        .get("message_id")
                                        .asText())
                                .isEqualTo(String.valueOf(TelegramFixtures.MESSAGE_ID));
                    });

            // then: a failed turn leaves nothing half-recorded behind it
            Optional<User> storedUser = userRepository.findByExternalId(SCENARIO.userExternalId());
            storedUser.ifPresent(user -> assertThat(ExpenseProposalRowUtils.expenseProposalRowsFor(
                            jdbcAggregateTemplate, user.id().orElseThrow()))
                    .as("expense_proposal rows for the conversation's user")
                    .isEmpty());
        }
    }
}
