package bot.finance.system;

import static bot.finance.common.TelegramTestBot.HANDLE_MESSAGE_FAILURE_TOKEN;
import static bot.finance.common.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.TelegramTestBot.recordedSendMessages;
import static bot.finance.common.TelegramTestBot.replyParameters;
import static bot.finance.common.WireMockStubs.telegramAcceptsSendMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.application.port.UserRepository;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.ExpenseProposalRowUtils;
import bot.finance.common.TelegramFixtures;
import bot.finance.common.WireMockStubs;
import bot.finance.common.containers.GrpcStubServer;
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
import org.springframework.test.context.TestPropertySource;

/**
 * The bot token below is what isolates this class: a differing property defeats Spring's context cache, so the
 * class gets its own context, a poll loop starting at offset 0, and a {@code /bot<token>/getUpdates} path no
 * other class's poller reaches.
 */
@TestPropertySource(properties = "telegram.bot.token=" + HANDLE_MESSAGE_FAILURE_TOKEN)
class HandleIncomingMessageFailureSystemTest extends AbstractSystemTest {

    private static final int UPDATE_ID = 42;
    private static final long CHAT_ID = 555L;
    private static final String CONVERSATION_ID = String.valueOf(CHAT_ID);
    private static final String MESSAGE_TEXT = "lunch 12 euro";
    private static final String NEXT_OFFSET = String.valueOf(UPDATE_ID + 1);
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
        telegramAcceptsSendMessage(HANDLE_MESSAGE_FAILURE_TOKEN);
        WireMockStubs.telegramReturnsNoUpdates(HANDLE_MESSAGE_FAILURE_TOKEN);
        WireMockStubs.telegramReturnsOnFirstPoll(
                HANDLE_MESSAGE_FAILURE_TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.textMessageUpdate(UPDATE_ID, CHAT_ID, CHAT_ID, MESSAGE_TEXT)));
        GrpcStubServer.failExtractionWith(Status.UNAVAILABLE.withDescription("AI connector unavailable"));
    }

    @Nested
    @DisplayName("unhappy path - the AI connector fails to extract intents")
    class UnhappyPath {

        @Test
        @DisplayName(
                "when the loop picks the update up - then one sendMessage reports nothing was noted, no "
                        + "expense_proposal row exists for the user, and the batch is still confirmed")
        void whenLoopPicksUpdateUp_thenFailureIsLoggedAndBatchIsStillConfirmed() {
            await("a follow-up getUpdates confirms the batch").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedPollsWithOffset(HANDLE_MESSAGE_FAILURE_TOKEN, NEXT_OFFSET))
                    .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                    .isNotEmpty());

            await("exactly one sendMessage reporting the FAILED outcome is recorded")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> {
                        List<LoggedRequest> sent = recordedSendMessages(HANDLE_MESSAGE_FAILURE_TOKEN);
                        log.debug("Recorded sendMessage requests: {}", sent);

                        assertThat(sent).hasSize(1);
                        LoggedRequest sendMessageRequest = sent.get(0);
                        assertThat(sendMessageRequest.formParameter("chat_id").getValues())
                                .containsExactly(CONVERSATION_ID);
                        assertThat(sendMessageRequest.formParameter("text").getValues())
                                .containsExactly(EXPECTED_TEXT);

                        assertThat(replyParameters(sendMessageRequest)
                                        .get("message_id")
                                        .asText())
                                .isEqualTo(String.valueOf(TelegramFixtures.MESSAGE_ID));
                    });

            Optional<User> storedUser = userRepository.findByExternalId(CONVERSATION_ID);
            storedUser.ifPresent(user -> assertThat(ExpenseProposalRowUtils.expenseProposalRowsFor(
                            jdbcAggregateTemplate, user.id().orElseThrow()))
                    .as("expense_proposal rows for the conversation's user")
                    .isEmpty());
        }
    }
}
