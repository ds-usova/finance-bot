package bot.finance.ai.system;

import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures;
import bot.finance.ai.common.stubs.LedgerChangeStreamStubs;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
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

    private void publishProposalCreated(long expenseId, long userId, String incomingMessageId, String txId) {
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

    private void publishProposalDeleted(long expenseId, long userId, String incomingMessageId, String txId) {
        LedgerChangeStreamStubs.publish(
                changeStreamKey,
                ChangeStreamEntryFixtures.proposalDiscarded(
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

    private void publishExpenseCreated(long expenseId, long userId, String incomingMessageId, String txId) {
        LedgerChangeStreamStubs.publish(
                changeStreamKey,
                ChangeStreamEntryFixtures.expenseRecorded(
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

        @Disabled("RS01: replaced by a happy-path scenario asserting one ACCEPTED row after a create then an accept")
        @Test
        @DisplayName("when a proposal's delete and expense insert share a txId - then one row is ACCEPTED with "
                + "both ids and pending is zero")
        void whenProposalDeleteAndExpenseInsertShareTxId_thenOneRowAcceptedWithBothIdsAndPendingZero() {
            // long userId = 9101L;
            // String incomingMessageId = "message-9101-1";
            // long proposalId = 91011L;
            // long expenseId = 91012L;
            // String txId = "tx-9101-1";
            // IncomingMessageRowUtils.insert(jdbcTemplate, userId, incomingMessageId, "spent 15 euros", Instant.now());
            //
            // publishProposalCreated(proposalId, userId, incomingMessageId, txId);
            // publishProposalDeleted(proposalId, userId, incomingMessageId, txId);
            // publishExpenseCreated(expenseId, userId, incomingMessageId, txId);
            //
            // Awaitility.await().atMost(BOUND).until(() -> RecordedExpenseRowUtils.findByProposalId(
            //                 jdbcTemplate, proposalId)
            //         .filter(row -> "ACCEPTED".equals(row.status()) && row.expenseId() != null)
            //         .isPresent());
            // Awaitility.await().atMost(BOUND).untilAsserted(() -> assertThat(
            //                 LedgerChangeStreamStubs.pending(changeStreamKey, GROUP))
            //         .isZero());
            //
            // RecordedExpenseRow recordedExpenseRow = RecordedExpenseRowUtils.findByProposalId(jdbcTemplate,
            // proposalId)
            //         .orElseThrow();
            // log.info("row: {}", recordedExpenseRow);
            // assertThat(recordedExpenseRow.proposalId()).isEqualTo(proposalId);
            // assertThat(recordedExpenseRow.expenseId()).isEqualTo(expenseId);
            // assertThat(recordedExpenseRow.amountMinorUnits()).isEqualTo(AMOUNT_MINOR_UNITS);
            // assertThat(recordedExpenseRow.currencyCode()).isEqualTo(CURRENCY_CODE);
            // assertThat(recordedExpenseRow.categoryId()).isEqualTo(CATEGORY_ID);
            // assertThat(recordedExpenseRow.categoryName()).isEqualTo(CATEGORY_NAME);
            // assertThat(recordedExpenseRow.groupingName()).isEqualTo(GROUPING_NAME);
        }

        @Disabled("RS01: the provisional DISCARDED-pending state no longer exists")
        @Test
        @DisplayName("when a PROPOSED row's delete arrives alone - then the row is DISCARDED")
        void whenProposedRowsDeleteArrivesAlone_thenRowIsDiscarded() {
            // long userId = 9102L;
            // String incomingMessageId = "message-9102-1";
            // long proposalId = 91021L;
            // String txId = "tx-9102-1";
            // IncomingMessageRowUtils.insert(jdbcTemplate, userId, incomingMessageId, "spent 15 euros", Instant.now());
            // publishProposalCreated(proposalId, userId, incomingMessageId, txId);
            // Awaitility.await().atMost(BOUND).until(() -> RecordedExpenseRowUtils.findByProposalId(
            //                 jdbcTemplate, proposalId)
            //         .map(row -> "PROPOSED".equals(row.status()))
            //         .orElse(false));
            //
            // publishProposalDeleted(proposalId, userId, incomingMessageId, txId);
            //
            // Awaitility.await().atMost(BOUND).untilAsserted(() -> {
            //     var row = RecordedExpenseRowUtils.findByProposalId(jdbcTemplate, proposalId);
            //     log.info("row: {}", row);
            //     assertThat(row).isPresent();
            //     assertThat(row.orElseThrow().status()).isEqualTo("DISCARDED");
            // });
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Disabled("RS01: rewritten against findByExpenseId, replaced by the Unhappy Path scenario named there")
        @Test
        @DisplayName(
                "when a proposal for an unregistered message arrives - then it is acknowledged and no row " + "exists")
        void whenProposalForUnregisteredMessageArrives_thenAcknowledgedAndNoRowExists() {
            // long userId = 9103L;
            // String incomingMessageId = "message-9103-1";
            // long proposalId = 91031L;
            // String txId = "tx-9103-1";
            //
            // publishProposalCreated(proposalId, userId, incomingMessageId, txId);
            //
            // Awaitility.await().atMost(BOUND).untilAsserted(() -> assertThat(
            //                 LedgerChangeStreamStubs.pending(changeStreamKey, GROUP))
            //         .isZero());
            // assertThat(RecordedExpenseRowUtils.findByProposalId(jdbcTemplate, proposalId))
            //         .isEmpty();
        }
    }
}
