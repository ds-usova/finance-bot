package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.recordedAnswerCallbackQueriesFor;
import static bot.finance.common.stubs.TelegramTestBot.recordedEditMessageReplyMarkupsFor;
import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsAnswerCallbackQuery;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static bot.finance.common.stubs.WireMockStubs.telegramDeliversOnce;
import static bot.finance.common.stubs.WireMockStubs.telegramReturnsNoUpdates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Covers a tap whose reference names no stored proposal or expense: nothing is resolved, and the buttons come
 * off anyway.
 */
class ResolveUnknownProposalsSystemTest extends AbstractSystemTest {

    private static final String TOKEN = TelegramTestBot.PROFILE_DEFAULT_TOKEN;

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.RESOLVE_UNKNOWN_PROPOSALS;

    private static final String FROM_ID_STRING = SCENARIO.userExternalId();
    private static final String NEXT_OFFSET = SCENARIO.nextOffset();
    private static final String CALLBACK_QUERY_ID = SCENARIO.callbackQueryId();
    private static final String EXPECTED_ANSWER_TEXT = "There is nothing left to resolve.";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final UUID unknownReference = UUID.randomUUID();

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private long userId;

    /**
     * The order below is load-bearing: the poll loop is already running, so the catch-all and response stubs must
     * be registered before the update-bearing stub, or the loop consumes the update before the response it
     * triggers can be recorded.
     */
    @BeforeEach
    void stubTelegram() {
        userId = UserRowUtils.storedUserId(userEntityRepository, FROM_ID_STRING);

        telegramReturnsNoUpdates(TOKEN);
        telegramAcceptsAnswerCallbackQuery(TOKEN);
        telegramAcceptsEditMessageReplyMarkup(TOKEN);
        telegramDeliversOnce(
                TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.callbackQueryUpdate(
                        SCENARIO.updateId(),
                        CALLBACK_QUERY_ID,
                        SCENARIO.userId(),
                        SCENARIO.chatId(),
                        TelegramFixtures.MESSAGE_ID,
                        "discard:" + unknownReference)));
    }

    @Nested
    @DisplayName("unhappy path - the tapped reference names no stored proposal or expense")
    class UnhappyPath {

        @Test
        @DisplayName("when the poll loop picks up a discard tap for an unknown reference - then nothing is "
                + "resolved and the buttons clear")
        void whenRunningPollLoopPicksUpDiscardCallbackQueryForUnknownReference_thenNothingIsResolvedButAcknowledged() {
            // then: the tap is consumed and its batch confirmed
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(TOKEN, NEXT_OFFSET))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            // then: a reference naming nothing of this user's records nothing
            List<ExpenseEntity> expenseRows = ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
            assertThat(expenseRows).as("expense rows for user %s", userId).isEmpty();

            // then: the tap is still answered, saying there was nothing to resolve
            await("one answerCallbackQuery is recorded").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedAnswerCallbackQueriesFor(TOKEN, SCENARIO))
                    .as("answerCallbackQuery requests recorded for token %s", TOKEN)
                    .isNotEmpty());
            List<LoggedRequest> answers = recordedAnswerCallbackQueriesFor(TOKEN, SCENARIO);
            assertThat(answers).as("exactly one answerCallbackQuery recorded").hasSize(1);
            LoggedRequest answer = answers.get(0);
            assertThat(answer.formParameter("callback_query_id").getValues())
                    .as("answerCallbackQuery callback_query_id form param")
                    .containsExactly(CALLBACK_QUERY_ID);
            assertThat(answer.formParameter("text").getValues())
                    .as("answerCallbackQuery text form param")
                    .containsExactly(EXPECTED_ANSWER_TEXT);

            // then: the buttons come off anyway, which is what repairs a report whose earlier edit was lost
            await("one editMessageReplyMarkup is recorded").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedEditMessageReplyMarkupsFor(TOKEN, SCENARIO))
                    .as("editMessageReplyMarkup requests recorded for token %s", TOKEN)
                    .isNotEmpty());
            List<LoggedRequest> edits = recordedEditMessageReplyMarkupsFor(TOKEN, SCENARIO);
            assertThat(edits).as("exactly one editMessageReplyMarkup recorded").hasSize(1);
            LoggedRequest edit = edits.get(0);
            assertThat(edit.formParameter("chat_id").getValues())
                    .as("editMessageReplyMarkup chat_id form param")
                    .containsExactly(SCENARIO.conversationId());
            assertThat(edit.formParameter("message_id").getValues())
                    .as("editMessageReplyMarkup message_id form param")
                    .containsExactly(String.valueOf(TelegramFixtures.MESSAGE_ID));
            assertThat(edit.formParameter("reply_markup").isPresent())
                    .as("editMessageReplyMarkup carries no reply_markup")
                    .isFalse();
        }
    }
}
