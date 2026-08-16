package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.ai.common.boot.PersistenceAdapterTest;
import bot.finance.ai.common.fixtures.RecordedChangeFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.SpendingRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapterTest
@Import(JdbcRecordedExpenseStoreAdapter.class)
class JdbcRecordedExpenseStoreAdapterTest {

    @Autowired
    private JdbcRecordedExpenseStoreAdapter adapter;

    @Autowired
    private RecordedExpenseEntityRepository repository;

    @Autowired
    private IncomingMessageEntityRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long registerMessage(long userId, String incomingMessageId) {
        IncomingMessageRowUtils.insert(jdbcTemplate, userId, incomingMessageId, "text", Instant.now());
        for (IncomingMessageEntity entity : messageRepository.findAll()) {
            if (entity.userId() == userId && entity.incomingMessageId().equals(incomingMessageId)) {
                return entity.id();
            }
        }
        throw new AssertionError("no message found for " + userId + "/" + incomingMessageId);
    }

    private SpendingRow spendingRow(long id, String incomingMessageId) {
        return spendingRow(id, incomingMessageId, RecordedChangeFixtures.DEFAULT_CATEGORY_NAME);
    }

    private SpendingRow spendingRow(long id, String incomingMessageId, String categoryName) {
        return spendingRow(
                id,
                incomingMessageId,
                RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                categoryName,
                RecordedChangeFixtures.DEFAULT_GROUPING_NAME);
    }

    private SpendingRow spendingRow(long id, String incomingMessageId, String description, long amountMinorUnits) {
        return spendingRow(
                id,
                incomingMessageId,
                description,
                amountMinorUnits,
                RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                RecordedChangeFixtures.DEFAULT_GROUPING_NAME);
    }

    private SpendingRow spendingRow(
            long id, String incomingMessageId, long categoryId, String categoryName, String groupingName) {
        return spendingRow(
                id,
                incomingMessageId,
                RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                categoryId,
                categoryName,
                groupingName);
    }

    private SpendingRow spendingRow(
            long id,
            String incomingMessageId,
            String description,
            long amountMinorUnits,
            long categoryId,
            String categoryName,
            String groupingName) {
        return new SpendingRow(
                id,
                RecordedChangeFixtures.DEFAULT_USER_ID,
                Optional.of(incomingMessageId),
                description,
                Optional.empty(),
                amountMinorUnits,
                CurrencyCode.of(RecordedChangeFixtures.DEFAULT_CURRENCY),
                categoryId,
                Optional.of(categoryName),
                Optional.of(groupingName));
    }

    private void insertDiscarded(long messageId, long proposalId, String transactionId) {
        insertDiscarded(
                messageId,
                proposalId,
                transactionId,
                RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS);
    }

    private void insertDiscarded(
            long messageId, long proposalId, String transactionId, String description, long amountMinorUnits) {
        RecordedExpenseRowUtils.insert(
                jdbcTemplate,
                messageId,
                RecordedChangeFixtures.DEFAULT_USER_ID,
                proposalId,
                null,
                description,
                null,
                amountMinorUnits,
                RecordedChangeFixtures.DEFAULT_CURRENCY,
                RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                "DISCARDED",
                transactionId,
                Instant.now());
    }

    private void insertLoneAccepted(long messageId, long expenseId, String transactionId) {
        RecordedExpenseRowUtils.insert(
                jdbcTemplate,
                messageId,
                RecordedChangeFixtures.DEFAULT_USER_ID,
                null,
                expenseId,
                RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                null,
                RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                RecordedChangeFixtures.DEFAULT_CURRENCY,
                RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                "ACCEPTED",
                transactionId,
                Instant.now());
    }

    private void insertAccepted(
            long messageId, long expenseId, long categoryId, String categoryName, String groupingName) {
        RecordedExpenseRowUtils.insert(
                jdbcTemplate,
                messageId,
                RecordedChangeFixtures.DEFAULT_USER_ID,
                null,
                expenseId,
                RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                null,
                RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                RecordedChangeFixtures.DEFAULT_CURRENCY,
                categoryId,
                categoryName,
                groupingName,
                "ACCEPTED",
                null,
                Instant.now());
    }

    @Nested
    @DisplayName("recording a proposed expense")
    class RecordProposed {

        @Test
        @DisplayName("when a registered message has no row - then one PROPOSED row holds the content and identifiers")
        void whenRegisteredMessageHasNoRow_thenOnePropsedRowHoldsContentAndIdentifiers() {
            String incomingMessageId = "record-proposed-new-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9001L;

            adapter.recordProposed(spendingRow(proposalId, incomingMessageId));

            RecordedExpenseEntity row = repository.findByProposalId(proposalId).orElseThrow();
            assertThat(row.status()).isEqualTo("PROPOSED");
            assertThat(row.expenseId()).isNull();
            assertThat(row.messageId()).isEqualTo(messageId);
            assertThat(row.userId()).isEqualTo(RecordedChangeFixtures.DEFAULT_USER_ID);
            assertThat(row.description()).isEqualTo(RecordedChangeFixtures.DEFAULT_DESCRIPTION);
            assertThat(row.merchant()).isNull();
            assertThat(row.amountMinorUnits()).isEqualTo(RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS);
            assertThat(row.currencyCode()).isEqualTo(RecordedChangeFixtures.DEFAULT_CURRENCY);
            assertThat(row.categoryId()).isEqualTo(RecordedChangeFixtures.DEFAULT_CATEGORY_ID);
            assertThat(row.categoryName()).isEqualTo(RecordedChangeFixtures.DEFAULT_CATEGORY_NAME);
            assertThat(row.groupingName()).isEqualTo(RecordedChangeFixtures.DEFAULT_GROUPING_NAME);
            assertThat(row.updatedAt()).isNotNull();
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("when called again for a row already ACCEPTED - then still one row, later name, still ACCEPTED")
        void whenCalledAgainForRowAlreadyAccepted_thenStillOneRowLaterNameStillAccepted() {
            String incomingMessageId = "record-proposed-existing-accepted-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9002L;
            long expenseId = 9102L;
            RecordedExpenseRowUtils.insert(
                    jdbcTemplate,
                    messageId,
                    RecordedChangeFixtures.DEFAULT_USER_ID,
                    proposalId,
                    expenseId,
                    RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                    null,
                    RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                    RecordedChangeFixtures.DEFAULT_CURRENCY,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                    "ACCEPTED",
                    null,
                    Instant.now());

            adapter.recordProposed(spendingRow(proposalId, incomingMessageId, "Restaurants"));

            RecordedExpenseEntity row = repository.findByProposalId(proposalId).orElseThrow();
            assertThat(row.status()).isEqualTo("ACCEPTED");
            assertThat(row.expenseId()).isEqualTo(expenseId);
            assertThat(row.categoryName()).isEqualTo("Restaurants");
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("when no message is registered under the row's identity - then no row is written")
        void whenNoMessageRegisteredUnderIdentity_thenNoRowWritten() {
            long proposalId = 9003L;

            adapter.recordProposed(spendingRow(proposalId, "record-proposed-unregistered-message"));

            assertThat(repository.findByProposalId(proposalId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("settling a proposal delete")
    class SettleProposalDeleted {

        @Test
        @DisplayName("when a PROPOSED row has no other row - then it becomes DISCARDED remembering the transaction")
        void whenProposedRowHasNoOtherRow_thenBecomesDiscardedRememberingTransaction() {
            String incomingMessageId = "settle-proposal-deleted-lone-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9101L;
            RecordedExpenseRowUtils.insert(
                    jdbcTemplate,
                    messageId,
                    RecordedChangeFixtures.DEFAULT_USER_ID,
                    proposalId,
                    null,
                    RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                    null,
                    RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                    RecordedChangeFixtures.DEFAULT_CURRENCY,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                    "PROPOSED",
                    null,
                    Instant.now());
            String transactionId = "settle-proposal-deleted-tx-1";

            adapter.settleProposalDeleted(spendingRow(proposalId, incomingMessageId), transactionId);

            RecordedExpenseEntity row = repository.findByProposalId(proposalId).orElseThrow();
            assertThat(row.status()).isEqualTo("DISCARDED");
            assertThat(row.movedInTx()).isEqualTo(transactionId);
        }

        @Test
        @DisplayName(
                "when an unpaired ACCEPTED row of the transaction matches - then one row remains ACCEPTED with both ids")
        void whenUnpairedAcceptedRowOfTransactionMatches_thenOneRowRemainsAcceptedWithBothIds() {
            String incomingMessageId = "settle-proposal-deleted-paired-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9111L;
            long expenseId = 9211L;
            String transactionId = "settle-proposal-deleted-tx-2";
            RecordedExpenseRowUtils.insert(
                    jdbcTemplate,
                    messageId,
                    RecordedChangeFixtures.DEFAULT_USER_ID,
                    proposalId,
                    null,
                    RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                    null,
                    RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                    RecordedChangeFixtures.DEFAULT_CURRENCY,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                    "PROPOSED",
                    null,
                    Instant.now());
            insertLoneAccepted(messageId, expenseId, transactionId);

            adapter.settleProposalDeleted(spendingRow(proposalId, incomingMessageId), transactionId);

            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(1);
            RecordedExpenseEntity row = repository.findByExpenseId(expenseId).orElseThrow();
            assertThat(row.status()).isEqualTo("ACCEPTED");
            assertThat(row.proposalId()).isEqualTo(proposalId);
        }

        @Test
        @DisplayName("when two unpaired ACCEPTED rows match - then the lower expense id takes the proposal id")
        void whenTwoUnpairedAcceptedRowsMatch_thenLowerExpenseIdTakesProposalId() {
            String incomingMessageId = "settle-proposal-deleted-two-accepted-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9121L;
            long lowerExpenseId = 9221L;
            long higherExpenseId = 9222L;
            String transactionId = "settle-proposal-deleted-tx-3";
            insertLoneAccepted(messageId, higherExpenseId, transactionId);
            insertLoneAccepted(messageId, lowerExpenseId, transactionId);

            adapter.settleProposalDeleted(spendingRow(proposalId, incomingMessageId), transactionId);

            RecordedExpenseEntity lower =
                    repository.findByExpenseId(lowerExpenseId).orElseThrow();
            assertThat(lower.status()).isEqualTo("ACCEPTED");
            assertThat(lower.proposalId()).isEqualTo(proposalId);
            RecordedExpenseEntity higher =
                    repository.findByExpenseId(higherExpenseId).orElseThrow();
            assertThat(higher.proposalId()).isNull();
        }

        @Test
        @DisplayName("when called again for a row already ACCEPTED with both ids - then the row is unchanged")
        void whenCalledAgainForRowAlreadyAcceptedWithBothIds_thenRowUnchanged() {
            String incomingMessageId = "settle-proposal-deleted-redelivered-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9131L;
            long expenseId = 9231L;
            String transactionId = "settle-proposal-deleted-tx-4";
            RecordedExpenseRowUtils.insert(
                    jdbcTemplate,
                    messageId,
                    RecordedChangeFixtures.DEFAULT_USER_ID,
                    proposalId,
                    expenseId,
                    RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                    null,
                    RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                    RecordedChangeFixtures.DEFAULT_CURRENCY,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                    "ACCEPTED",
                    transactionId,
                    Instant.now());

            adapter.settleProposalDeleted(spendingRow(proposalId, incomingMessageId), transactionId);

            RecordedExpenseEntity row = repository.findByProposalId(proposalId).orElseThrow();
            assertThat(row.status()).isEqualTo("ACCEPTED");
            assertThat(row.expenseId()).isEqualTo(expenseId);
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("when no message is registered under the row's identity - then nothing changes")
        void whenNoMessageRegisteredForProposal_thenNothingChanges() {
            long proposalId = 9141L;

            adapter.settleProposalDeleted(
                    spendingRow(proposalId, "settle-proposal-deleted-unregistered-message"),
                    "settle-proposal-deleted-tx-5");

            assertThat(repository.findByProposalId(proposalId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("settling an expense insert")
    class SettleExpenseInserted {

        @Test
        @DisplayName(
                "when a DISCARDED row of the transaction matches - then it becomes ACCEPTED holding the expense id")
        void whenDiscardedRowOfTransactionMatches_thenBecomesAcceptedHoldingExpenseId() {
            String incomingMessageId = "settle-expense-inserted-match-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9301L;
            long expenseId = 9401L;
            String transactionId = "settle-expense-inserted-tx-1";
            insertDiscarded(messageId, proposalId, transactionId);

            adapter.settleExpenseInserted(spendingRow(expenseId, incomingMessageId), transactionId);

            RecordedExpenseEntity row = repository.findByProposalId(proposalId).orElseThrow();
            assertThat(row.status()).isEqualTo("ACCEPTED");
            assertThat(row.expenseId()).isEqualTo(expenseId);
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("when called twice for two equal-content DISCARDED rows - then the lower proposal id pairs first")
        void whenCalledTwiceForTwoEqualContentDiscardedRows_thenLowerProposalIdPairsFirst() {
            String incomingMessageId = "settle-expense-inserted-two-discarded-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long lowerProposalId = 9311L;
            long higherProposalId = 9312L;
            long firstExpenseId = 9411L;
            long secondExpenseId = 9412L;
            String transactionId = "settle-expense-inserted-tx-2";
            insertDiscarded(messageId, higherProposalId, transactionId);
            insertDiscarded(messageId, lowerProposalId, transactionId);

            adapter.settleExpenseInserted(spendingRow(firstExpenseId, incomingMessageId), transactionId);
            adapter.settleExpenseInserted(spendingRow(secondExpenseId, incomingMessageId), transactionId);

            RecordedExpenseEntity first =
                    repository.findByProposalId(lowerProposalId).orElseThrow();
            assertThat(first.status()).isEqualTo("ACCEPTED");
            assertThat(first.expenseId()).isEqualTo(firstExpenseId);
            RecordedExpenseEntity second =
                    repository.findByProposalId(higherProposalId).orElseThrow();
            assertThat(second.status()).isEqualTo("ACCEPTED");
            assertThat(second.expenseId()).isEqualTo(secondExpenseId);
        }

        @Test
        @DisplayName("when three DISCARDED rows differ in content - then each pairs with its matching content")
        void whenThreeDiscardedRowsDifferInContent_thenEachPairsWithMatchingContent() {
            String incomingMessageId = "settle-expense-inserted-three-discarded-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            String transactionId = "settle-expense-inserted-tx-3";
            long lunchProposalId = 9321L;
            long taxiProposalId = 9322L;
            long groceriesProposalId = 9323L;
            insertDiscarded(messageId, lunchProposalId, transactionId, "lunch", 1500L);
            insertDiscarded(messageId, taxiProposalId, transactionId, "taxi", 800L);
            insertDiscarded(messageId, groceriesProposalId, transactionId, "groceries", 4200L);
            long lunchExpenseId = 9421L;
            long taxiExpenseId = 9422L;
            long groceriesExpenseId = 9423L;

            adapter.settleExpenseInserted(spendingRow(taxiExpenseId, incomingMessageId, "taxi", 800L), transactionId);
            adapter.settleExpenseInserted(
                    spendingRow(lunchExpenseId, incomingMessageId, "lunch", 1500L), transactionId);
            adapter.settleExpenseInserted(
                    spendingRow(groceriesExpenseId, incomingMessageId, "groceries", 4200L), transactionId);

            assertThat(repository
                            .findByProposalId(lunchProposalId)
                            .orElseThrow()
                            .expenseId())
                    .isEqualTo(lunchExpenseId);
            assertThat(repository.findByProposalId(taxiProposalId).orElseThrow().expenseId())
                    .isEqualTo(taxiExpenseId);
            assertThat(repository
                            .findByProposalId(groceriesProposalId)
                            .orElseThrow()
                            .expenseId())
                    .isEqualTo(groceriesExpenseId);
        }

        @Test
        @DisplayName("when the only DISCARDED match is of another transaction - then a lone ACCEPTED row is written")
        void whenOnlyDiscardedMatchOfAnotherTransaction_thenLoneAcceptedRowWritten() {
            String incomingMessageId = "settle-expense-inserted-other-tx-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long proposalId = 9331L;
            String otherTransactionId = "settle-expense-inserted-other-tx";
            insertDiscarded(messageId, proposalId, otherTransactionId);
            long expenseId = 9431L;
            String transactionId = "settle-expense-inserted-tx-4a";

            adapter.settleExpenseInserted(spendingRow(expenseId, incomingMessageId), transactionId);

            RecordedExpenseEntity discarded =
                    repository.findByProposalId(proposalId).orElseThrow();
            assertThat(discarded.status()).isEqualTo("DISCARDED");
            RecordedExpenseEntity loneAccepted =
                    repository.findByExpenseId(expenseId).orElseThrow();
            assertThat(loneAccepted.status()).isEqualTo("ACCEPTED");
            assertThat(loneAccepted.proposalId()).isNull();
            assertThat(loneAccepted.movedInTx()).isEqualTo(transactionId);
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("when the only DISCARDED match is of another message - then a lone ACCEPTED row is written")
        void whenOnlyDiscardedMatchOfAnotherMessage_thenLoneAcceptedRowWritten() {
            long otherMessageId =
                    registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, "settle-expense-inserted-other-message-a");
            String incomingMessageId = "settle-expense-inserted-other-message-b";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            String transactionId = "settle-expense-inserted-tx-4b";
            long proposalId = 9341L;
            insertDiscarded(otherMessageId, proposalId, transactionId);
            long expenseId = 9441L;

            adapter.settleExpenseInserted(spendingRow(expenseId, incomingMessageId), transactionId);

            RecordedExpenseEntity discarded =
                    repository.findByProposalId(proposalId).orElseThrow();
            assertThat(discarded.status()).isEqualTo("DISCARDED");
            RecordedExpenseEntity loneAccepted =
                    repository.findByExpenseId(expenseId).orElseThrow();
            assertThat(loneAccepted.status()).isEqualTo("ACCEPTED");
            assertThat(loneAccepted.messageId()).isEqualTo(messageId);
        }

        @Test
        @DisplayName("when called again for a row already keyed by the expense id - then still one row, later name")
        void whenCalledAgainForRowAlreadyKeyedByExpenseId_thenStillOneRowLaterName() {
            String incomingMessageId = "settle-expense-inserted-redelivered-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long expenseId = 9451L;
            String transactionId = "settle-expense-inserted-tx-5";
            insertLoneAccepted(messageId, expenseId, transactionId);

            adapter.settleExpenseInserted(spendingRow(expenseId, incomingMessageId, "Restaurants"), transactionId);

            RecordedExpenseEntity row = repository.findByExpenseId(expenseId).orElseThrow();
            assertThat(row.categoryName()).isEqualTo("Restaurants");
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("settling an acceptance's two halves concurrently")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class SettleConcurrently {

        private static final long USER_ID = RecordedChangeFixtures.DEFAULT_USER_ID;
        private static final int ITERATIONS = 20;

        @Test
        @DisplayName("when both settle calls run at once, repeatedly - then every run ends with one ACCEPTED row "
                + "holding both ids")
        void whenBothSettleCallsRunAtOnceRepeatedly_thenEveryRunEndsWithOneAcceptedRowHoldingBothIds()
                throws Exception {
            String incomingMessageId = "settle-concurrently-message";
            long messageId = registerMessage(USER_ID, incomingMessageId);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    long proposalId = 8_000_000L + i * 2L;
                    long expenseId = proposalId + 1;
                    String transactionId = "settle-concurrently-tx-" + i;
                    RecordedExpenseRowUtils.insert(
                            jdbcTemplate,
                            messageId,
                            USER_ID,
                            proposalId,
                            null,
                            RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                            null,
                            RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                            RecordedChangeFixtures.DEFAULT_CURRENCY,
                            RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                            RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                            RecordedChangeFixtures.DEFAULT_GROUPING_NAME,
                            "PROPOSED",
                            null,
                            Instant.now());

                    SpendingRow proposalRow = spendingRow(proposalId, incomingMessageId);
                    SpendingRow expenseRow = spendingRow(expenseId, incomingMessageId);

                    Callable<Void> deleteCall = () -> {
                        adapter.settleProposalDeleted(proposalRow, transactionId);
                        return null;
                    };
                    Callable<Void> insertCall = () -> {
                        adapter.settleExpenseInserted(expenseRow, transactionId);
                        return null;
                    };

                    List<Future<Void>> results =
                            executor.invokeAll(List.of(deleteCall, insertCall), 5, TimeUnit.SECONDS);
                    for (Future<Void> result : results) {
                        result.get();
                    }

                    assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageId))
                            .isEqualTo(1);
                    RecordedExpenseRowUtils.RecordedExpenseRow row = RecordedExpenseRowUtils.findByProposalId(
                                    jdbcTemplate, proposalId)
                            .orElseThrow();
                    assertThat(row.status()).isEqualTo("ACCEPTED");
                    assertThat(row.expenseId()).isEqualTo(expenseId);

                    RecordedExpenseRowUtils.deleteAll(jdbcTemplate);
                }
            } finally {
                executor.shutdownNow();
                RecordedExpenseRowUtils.deleteAll(jdbcTemplate);
                jdbcTemplate.update(
                        "DELETE FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?",
                        USER_ID,
                        incomingMessageId);
            }
        }
    }

    @Nested
    @DisplayName("refiling and removing a recorded expense")
    class RefileAndRemoveExpense {

        @Test
        @DisplayName(
                "when refileExpense is called for an ACCEPTED row - then it holds the new category, staying ACCEPTED")
        void whenRefileExpenseCalledForAcceptedRow_thenHoldsNewCategoryStayingAccepted() {
            String incomingMessageId = "refile-expense-accepted-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long expenseId = 9501L;
            insertAccepted(
                    messageId,
                    expenseId,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME);
            long newCategoryId = 77L;

            adapter.refileExpense(spendingRow(expenseId, incomingMessageId, newCategoryId, "Travel", "Transport"));

            RecordedExpenseEntity row = repository.findByExpenseId(expenseId).orElseThrow();
            assertThat(row.status()).isEqualTo("ACCEPTED");
            assertThat(row.categoryId()).isEqualTo(newCategoryId);
            assertThat(row.categoryName()).isEqualTo("Travel");
            assertThat(row.groupingName()).isEqualTo("Transport");
        }

        @Test
        @DisplayName("when removeExpense is called with an ACCEPTED row's expense id - then it is gone, message stays")
        void whenRemoveExpenseCalledWithAcceptedRowExpenseId_thenGoneMessageStays() {
            String incomingMessageId = "remove-expense-accepted-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            long expenseId = 9502L;
            insertAccepted(
                    messageId,
                    expenseId,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME);

            adapter.removeExpense(expenseId);

            assertThat(repository.findByExpenseId(expenseId)).isEmpty();
            assertThat(IncomingMessageRowUtils.count(
                            jdbcTemplate, RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("when no row exists under the expense id - then refileExpense and removeExpense throw nothing")
        void whenNoRowUnderExpenseId_thenRefileAndRemoveThrowNothing() {
            long expenseId = 9599L;

            assertThatCode(() -> {
                        adapter.refileExpense(
                                spendingRow(expenseId, "refile-expense-missing-message", 1L, "Other", "Misc"));
                        adapter.removeExpense(expenseId);
                    })
                    .doesNotThrowAnyException();

            assertThat(repository.findByExpenseId(expenseId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("renaming a category or a grouping")
    class RenameCategoryAndGrouping {

        @Test
        @DisplayName("when renameCategory is called for one category - then only its rows hold the new name")
        void whenRenameCategoryCalledForOneCategory_thenOnlyItsRowsHoldNewName() {
            long messageIdA = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, "rename-category-message-a");
            long messageIdB = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, "rename-category-message-b");
            long targetCategoryId = 42L;
            long otherCategoryId = 43L;
            long targetExpenseId = 9601L;
            long otherExpenseId = 9602L;
            insertAccepted(messageIdA, targetExpenseId, targetCategoryId, "Old Name", "Food");
            insertAccepted(messageIdB, otherExpenseId, otherCategoryId, "Untouched", "Food");

            adapter.renameCategory(targetCategoryId, "New Name");

            assertThat(repository.findByExpenseId(targetExpenseId).orElseThrow().categoryName())
                    .isEqualTo("New Name");
            assertThat(repository.findByExpenseId(otherExpenseId).orElseThrow().categoryName())
                    .isEqualTo("Untouched");
        }

        @Test
        @DisplayName("when renameGrouping is called for one person - then only that person's rows are renamed")
        void whenRenameGroupingCalledForOnePerson_thenOnlyThatPersonRowsRenamed() {
            long firstUserId = RecordedChangeFixtures.DEFAULT_USER_ID;
            long secondUserId = RecordedChangeFixtures.DEFAULT_USER_ID + 1;
            long firstMessageId = registerMessage(firstUserId, "rename-grouping-first-user-message");
            long secondMessageId = registerMessage(secondUserId, "rename-grouping-second-user-message");
            long firstExpenseId = 9611L;
            long secondExpenseId = 9612L;
            RecordedExpenseRowUtils.insert(
                    jdbcTemplate,
                    firstMessageId,
                    firstUserId,
                    null,
                    firstExpenseId,
                    RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                    null,
                    RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                    RecordedChangeFixtures.DEFAULT_CURRENCY,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    "Dining",
                    "ACCEPTED",
                    null,
                    Instant.now());
            RecordedExpenseRowUtils.insert(
                    jdbcTemplate,
                    secondMessageId,
                    secondUserId,
                    null,
                    secondExpenseId,
                    RecordedChangeFixtures.DEFAULT_DESCRIPTION,
                    null,
                    RecordedChangeFixtures.DEFAULT_AMOUNT_MINOR_UNITS,
                    RecordedChangeFixtures.DEFAULT_CURRENCY,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    RecordedChangeFixtures.DEFAULT_CATEGORY_NAME,
                    "Dining",
                    "ACCEPTED",
                    null,
                    Instant.now());

            adapter.renameGrouping(firstUserId, "Dining", "Food");

            assertThat(repository.findByExpenseId(firstExpenseId).orElseThrow().groupingName())
                    .isEqualTo("Food");
            assertThat(repository.findByExpenseId(secondExpenseId).orElseThrow().groupingName())
                    .isEqualTo("Dining");
        }

        @Test
        @DisplayName("when no row matches the category or the grouping - then both calls throw nothing")
        void whenNoRowMatchesCategoryOrGrouping_thenBothCallsThrowNothing() {
            assertThatCode(() -> {
                        adapter.renameCategory(999_999L, "Unused");
                        adapter.renameGrouping(999_999L, "Nowhere", "Somewhere");
                    })
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("abandoning an acceptance")
    class AbandonAcceptance {

        @Test
        @DisplayName(
                "when abandonAcceptance is called for the message's transaction - then only its row becomes UNKNOWN")
        void whenAbandonAcceptanceCalledForTransaction_thenOnlyItsRowBecomesUnknown() {
            String incomingMessageId = "abandon-acceptance-message";
            long messageId = registerMessage(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId);
            String transactionId = "abandon-acceptance-tx";
            String otherTransactionId = "abandon-acceptance-other-tx";
            long discardedProposalId = 9701L;
            long otherDiscardedProposalId = 9702L;
            long loneExpenseId = 9801L;
            insertDiscarded(messageId, discardedProposalId, transactionId);
            insertDiscarded(messageId, otherDiscardedProposalId, otherTransactionId);
            insertLoneAccepted(messageId, loneExpenseId, transactionId);

            adapter.abandonAcceptance(
                    new MessageIdentity(RecordedChangeFixtures.DEFAULT_USER_ID, incomingMessageId), transactionId);

            assertThat(repository
                            .findByProposalId(discardedProposalId)
                            .orElseThrow()
                            .status())
                    .isEqualTo("UNKNOWN");
            assertThat(repository
                            .findByProposalId(otherDiscardedProposalId)
                            .orElseThrow()
                            .status())
                    .isEqualTo("DISCARDED");
            assertThat(repository.findByExpenseId(loneExpenseId).orElseThrow().status())
                    .isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("when no row of the message matches the transaction - then abandonAcceptance throws nothing")
        void whenNoRowOfMessageMatchesTransaction_thenAbandonAcceptanceThrowsNothing() {
            assertThatCode(() -> adapter.abandonAcceptance(
                            new MessageIdentity(
                                    RecordedChangeFixtures.DEFAULT_USER_ID, "abandon-acceptance-missing-message"),
                            "abandon-acceptance-missing-tx"))
                    .doesNotThrowAnyException();
        }
    }

    // The scenarios below need a store that fails in a way the healthy containerized Postgres cannot be
    // made to. Each constructs its own adapter over a Mockito mock and calls the adapter's own public
    // methods directly - it is still the adapter under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked repository, not the containerized database")
    class WithAMockedRepository {

        private final RecordedExpenseEntityRepository mockedRepository = mock(RecordedExpenseEntityRepository.class);
        private final IncomingMessageEntityRepository mockedMessageRepository =
                mock(IncomingMessageEntityRepository.class);
        private final JdbcRecordedExpenseStoreAdapter mockedAdapter =
                new JdbcRecordedExpenseStoreAdapter(mockedRepository, mockedMessageRepository);

        private static Stream<DataAccessException> unavailableExceptions() {
            return Stream.of(
                    new DataAccessResourceFailureException("connection refused"),
                    new TransientDataAccessResourceException("timed out"));
        }

        @ParameterizedTest
        @MethodSource("unavailableExceptions")
        @DisplayName("when the repository throws a resource-failure or transient exception - then throws "
                + "MessageStoreUnavailableException")
        void whenRepositoryThrowsResourceFailureOrTransientException_thenThrowsMessageStoreUnavailableException(
                DataAccessException frameworkException) {
            when(mockedRepository.upsertProposed(
                            anyLong(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyString(),
                            any(Instant.class)))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.recordProposed(spendingRow(1L, "mocked-message")))
                    .isInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when the repository throws another DataAccessException - then throws MessageStoreFailedException, "
                        + "not its subtype")
        void whenRepositoryThrowsAnotherDataAccessException_thenThrowsMessageStoreFailedExceptionNotSubtype() {
            DataIntegrityViolationException frameworkException =
                    new DataIntegrityViolationException("constraint violated");
            when(mockedRepository.upsertProposed(
                            anyLong(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyLong(),
                            anyString(),
                            anyString(),
                            any(Instant.class)))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.recordProposed(spendingRow(2L, "mocked-message-2")))
                    .isInstanceOf(MessageStoreFailedException.class)
                    .isNotInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }
}
