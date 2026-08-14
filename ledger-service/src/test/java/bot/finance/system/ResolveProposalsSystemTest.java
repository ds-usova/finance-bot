package bot.finance.system;

import static bot.finance.common.stubs.TelegramTestBot.RESOLVE_PROPOSALS_TOKEN;
import static bot.finance.common.stubs.TelegramTestBot.recordedAnswerCallbackQueriesFor;
import static bot.finance.common.stubs.TelegramTestBot.recordedEditMessageReplyMarkupsIn;
import static bot.finance.common.stubs.TelegramTestBot.recordedPollsWithOffset;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsAnswerCallbackQuery;
import static bot.finance.common.stubs.WireMockStubs.telegramAcceptsEditMessageReplyMarkup;
import static bot.finance.common.stubs.WireMockStubs.telegramDeliversOnce;
import static bot.finance.common.stubs.WireMockStubs.telegramReturnsNoUpdates;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.ExpenseEntity;
import bot.finance.adapter.persistence.ExpenseProposalEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.TelegramFixtures;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.stubs.TelegramTestBot;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
@TestPropertySource(properties = "telegram.bot.token=" + RESOLVE_PROPOSALS_TOKEN)
class ResolveProposalsSystemTest extends AbstractSystemTest {

    private static final TelegramTestBot.TelegramScenario SCENARIO = TelegramTestBot.RESOLVE_PROPOSALS;

    private static final String FROM_ID_STRING = SCENARIO.userExternalId();
    private static final String NEXT_OFFSET = SCENARIO.nextOffset();
    private static final String CALLBACK_QUERY_ID = SCENARIO.callbackQueryId();
    private static final String EXPECTED_ANSWER_TEXT = "Confirmed 2 expenses.";

    private static final String GROUPING_NAME = "Groceries";
    private static final String CATEGORY_NAME = "Supermarkets";
    private static final String DESCRIPTION_1 = "lunch";
    private static final String DESCRIPTION_2 = "dinner";
    private static final String MERCHANT = "Cafe";
    private static final long AMOUNT_MINOR_UNITS = 1230L;
    private static final String CURRENCY_CODE = "EUR";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String reference = UUID.randomUUID().toString();

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
    void stubTelegramAndSeedProposals() {
        userId = UserRowUtils.storedUserId(userEntityRepository, FROM_ID_STRING);
        long groupingId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, GROUPING_NAME);
        long categoryId = CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, CATEGORY_NAME);
        Instant now = Instant.now();
        ExpenseProposalRowUtils.storedProposal(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                DESCRIPTION_1,
                MERCHANT,
                AMOUNT_MINOR_UNITS,
                CURRENCY_CODE,
                reference,
                now);
        ExpenseProposalRowUtils.storedProposal(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                DESCRIPTION_2,
                MERCHANT,
                AMOUNT_MINOR_UNITS,
                CURRENCY_CODE,
                reference,
                now);

        telegramReturnsNoUpdates(RESOLVE_PROPOSALS_TOKEN);
        telegramAcceptsAnswerCallbackQuery(RESOLVE_PROPOSALS_TOKEN);
        telegramAcceptsEditMessageReplyMarkup(RESOLVE_PROPOSALS_TOKEN);
        telegramDeliversOnce(
                RESOLVE_PROPOSALS_TOKEN,
                TelegramFixtures.updatesResponse(TelegramFixtures.callbackQueryUpdate(
                        SCENARIO.updateId(),
                        CALLBACK_QUERY_ID,
                        SCENARIO.userId(),
                        SCENARIO.chatId(),
                        TelegramFixtures.MESSAGE_ID,
                        "accept:" + reference)));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the poll loop picks up an accept tap - then both proposals become expenses and the tap is "
                + "answered")
        void whenRunningPollLoopPicksUpAcceptCallbackQuery_thenProposalsAreAcceptedAndAcknowledged() {
            // then: the tap is consumed and its batch confirmed
            await("the batch is confirmed with a follow-up getUpdates carrying offset=" + NEXT_OFFSET)
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(recordedPollsWithOffset(RESOLVE_PROPOSALS_TOKEN, NEXT_OFFSET))
                            .as("follow-up getUpdates polls carrying offset=%s", NEXT_OFFSET)
                            .isNotEmpty());

            // then: nothing the message proposed is left pending
            List<ExpenseProposalEntity> remainingProposals =
                    ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
            assertThat(remainingProposals)
                    .as("expense_proposal rows for user %s", userId)
                    .isEmpty();

            // then: both proposals are now expenses, each still naming the message it came from
            List<ExpenseEntity> expenseRows = ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
            assertThat(expenseRows).as("expense rows for user %s", userId).hasSize(2);
            assertThat(expenseRows)
                    .as("every accepted expense carries the resolved message reference")
                    .allSatisfy(row -> assertThat(row.incomingMessageId()).isEqualTo(reference));

            // then: the tap is answered, telling the user what it did
            await("one answerCallbackQuery is recorded").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedAnswerCallbackQueriesFor(RESOLVE_PROPOSALS_TOKEN, SCENARIO))
                    .as("answerCallbackQuery requests recorded for token %s", RESOLVE_PROPOSALS_TOKEN)
                    .isNotEmpty());
            List<LoggedRequest> answers = recordedAnswerCallbackQueriesFor(RESOLVE_PROPOSALS_TOKEN, SCENARIO);
            assertThat(answers).as("exactly one answerCallbackQuery recorded").hasSize(1);
            LoggedRequest answer = answers.get(0);
            assertThat(answer.formParameter("callback_query_id").getValues())
                    .as("answerCallbackQuery callback_query_id form param")
                    .containsExactly(CALLBACK_QUERY_ID);
            assertThat(answer.formParameter("text").getValues())
                    .as("answerCallbackQuery text form param")
                    .containsExactly(EXPECTED_ANSWER_TEXT);

            // then: the report loses its buttons, so it cannot be resolved twice
            await("one editMessageReplyMarkup is recorded").atMost(TIMEOUT).untilAsserted(() -> assertThat(
                            recordedEditMessageReplyMarkupsIn(RESOLVE_PROPOSALS_TOKEN, SCENARIO))
                    .as("editMessageReplyMarkup requests recorded for token %s", RESOLVE_PROPOSALS_TOKEN)
                    .isNotEmpty());
            List<LoggedRequest> edits = recordedEditMessageReplyMarkupsIn(RESOLVE_PROPOSALS_TOKEN, SCENARIO);
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
