package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils.RecordedExpenseRow;
import bot.finance.ai.common.stubs.LedgerChangeStreamStubs;
import java.time.Duration;
import java.time.Instant;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered by letting {@code ChangeStreamConsumer} read {@link AbstractMemorySystemTest}'s own stream key — never
 * by calling {@code run()} directly. Every entry is published through {@link LedgerChangeStreamStubs}, standing in
 * for the ledger's own writes to {@code ledger.cdc}.
 */
class LearnMessageOutcomeSystemTest extends AbstractMemorySystemTest {

    private static final String GROUP = "ai-connector";
    private static final Duration BOUND = Duration.ofSeconds(10);

    private static final String DESCRIPTION = "lunch";
    private static final String MERCHANT = "Deli Co";
    private static final String AMOUNT = "15.00";
    private static final String CURRENCY_CODE = "EUR";
    private static final long CATEGORY_ID = 42L;
    private static final String CATEGORY_NAME = "Lunch";
    private static final long GROUPING_ID = 7L;
    private static final String GROUPING_NAME = "Food";

    private void publishProposalCreated(long expenseId, long userId, String incomingMessageId) {
        LedgerChangeStreamStubs.publish(
                changeStreamKey,
                ChangeStreamEntryFixtures.proposalCreated(
                        expenseId,
                        userId,
                        incomingMessageId,
                        expenseId,
                        DESCRIPTION,
                        MERCHANT,
                        AMOUNT,
                        CURRENCY_CODE,
                        CATEGORY_ID,
                        CATEGORY_NAME,
                        GROUPING_ID,
                        GROUPING_NAME));
    }

    private void publishProposalAccepted(long expenseId, long userId, String incomingMessageId) {
        LedgerChangeStreamStubs.publish(
                changeStreamKey,
                ChangeStreamEntryFixtures.proposalAccepted(
                        expenseId,
                        userId,
                        incomingMessageId,
                        expenseId,
                        DESCRIPTION,
                        MERCHANT,
                        AMOUNT,
                        CURRENCY_CODE,
                        CATEGORY_ID,
                        CATEGORY_NAME,
                        GROUPING_ID,
                        GROUPING_NAME));
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when a proposal is created and then accepted - then one row is ACCEPTED with its content "
                + "and pending is zero")
        void whenProposalCreatedThenAccepted_thenOneRowAcceptedWithContentAndPendingZero() {
            long userId = 9101L;
            String incomingMessageId = "message-9101-1";
            long expenseId = 91011L;
            IncomingMessageRowUtils.insert(jdbcTemplate, userId, incomingMessageId, "spent 15 euros", Instant.now());
            long messageId = IncomingMessageRowUtils.id(jdbcTemplate, userId, incomingMessageId);

            publishProposalCreated(expenseId, userId, incomingMessageId);
            publishProposalAccepted(expenseId, userId, incomingMessageId);

            Awaitility.await().atMost(BOUND).until(() -> RecordedExpenseRowUtils.findByExpenseId(
                            jdbcTemplate, expenseId)
                    .filter(row -> "ACCEPTED".equals(row.status()))
                    .isPresent());
            Awaitility.await().atMost(BOUND).untilAsserted(() -> assertThat(
                            LedgerChangeStreamStubs.pending(changeStreamKey, GROUP))
                    .isZero());

            RecordedExpenseRow row = RecordedExpenseRowUtils.findByExpenseId(jdbcTemplate, expenseId)
                    .orElseThrow();
            log.info("row: {}", row);
            assertThat(row.status()).isEqualTo("ACCEPTED");
            assertThat(row.expenseId()).isEqualTo(expenseId);
            assertThat(row.description()).isEqualTo(DESCRIPTION);
            assertThat(row.merchant()).isEqualTo(MERCHANT);
            assertThat(row.amount()).isEqualTo(AMOUNT);
            assertThat(row.currencyCode()).isEqualTo(CURRENCY_CODE);
            assertThat(row.categoryId()).isEqualTo(CATEGORY_ID);
            assertThat(row.categoryName()).isEqualTo(CATEGORY_NAME);
            assertThat(row.groupingId()).isEqualTo(GROUPING_ID);
            assertThat(row.groupingName()).isEqualTo(GROUPING_NAME);
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .as("exactly one row for that expenseId")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when a proposal for an unregistered message arrives - then pending is zero and no row exists")
        void whenProposalForUnregisteredMessageArrives_thenPendingZeroAndNoRowExists() {
            long userId = 9103L;
            String incomingMessageId = "message-9103-1";
            long expenseId = 91031L;

            publishProposalCreated(expenseId, userId, incomingMessageId);

            Awaitility.await().atMost(BOUND).untilAsserted(() -> assertThat(
                            LedgerChangeStreamStubs.pending(changeStreamKey, GROUP))
                    .isZero());
            assertThat(RecordedExpenseRowUtils.findByExpenseId(jdbcTemplate, expenseId))
                    .isEmpty();
        }
    }
}
