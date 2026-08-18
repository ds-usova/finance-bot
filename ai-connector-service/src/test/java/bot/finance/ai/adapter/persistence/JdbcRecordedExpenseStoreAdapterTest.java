package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.ai.common.boot.PersistenceAdapterTest;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils;
import bot.finance.ai.common.rows.RecordedExpenseRowUtils.RecordedExpenseRow;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import bot.finance.ai.domain.value.CategoryRef;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapterTest
@Import(JdbcRecordedExpenseStoreAdapter.class)
class JdbcRecordedExpenseStoreAdapterTest {

    @Autowired
    private JdbcRecordedExpenseStoreAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private void registerMessage(long userId, String incomingMessageId) {
        IncomingMessageRowUtils.insert(jdbcTemplate, userId, incomingMessageId, "text", Instant.now());
    }

    private SpendingRow row(
            long expenseId,
            long userId,
            Optional<String> incomingMessageId,
            String description,
            Optional<String> merchant,
            String amount,
            String currencyCode,
            CategoryRef category,
            Optional<CategoryRef> grouping) {
        return new SpendingRow(
                expenseId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amount,
                CurrencyCode.of(currencyCode),
                category,
                grouping);
    }

    private void seedRow(
            long messageId,
            long userId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName,
            String status,
            long appliedMs,
            long appliedSeq) {
        RecordedExpenseRowUtils.insert(
                jdbcTemplate,
                messageId,
                userId,
                expenseId,
                description,
                merchant,
                amount,
                currencyCode,
                categoryId,
                categoryName,
                groupingId,
                groupingName,
                status,
                appliedMs,
                appliedSeq,
                Instant.now());
    }

    private Optional<RecordedExpenseRow> rowFor(long expenseId) {
        return RecordedExpenseRowUtils.findByExpenseId(jdbcTemplate, expenseId);
    }

    @Nested
    @DisplayName("applying an entry")
    class Apply {

        @Test
        @DisplayName("when a registered message has no row - then one PROPOSED row holds the content and identifiers")
        void whenRegisteredMessageHasNoRow_thenOnePropsedRowHoldsContentAndIdentifiers() {
            long userId = 97001L;
            String messageId = "ri01-insert";
            registerMessage(userId, messageId);
            long expenseId = 971001L;
            SpendingRow entry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.of("Cafe"),
                    "12.50",
                    "EUR",
                    new CategoryRef(5L, "Groceries"),
                    Optional.of(new CategoryRef(2L, "Food")));
            StreamPosition position = new StreamPosition(1_700_000_000_000L, 3L);

            adapter.apply(entry, RecordedStatus.PROPOSED, position);

            Optional<RecordedExpenseRow> result = rowFor(expenseId);
            assertThat(result).isPresent();
            RecordedExpenseRow r = result.orElseThrow();
            assertThat(r.status()).isEqualTo("PROPOSED");
            assertThat(r.description()).isEqualTo("lunch");
            assertThat(r.merchant()).isEqualTo("Cafe");
            assertThat(r.amount()).isEqualTo("12.50");
            assertThat(r.currencyCode()).isEqualTo("EUR");
            assertThat(r.categoryId()).isEqualTo(5L);
            assertThat(r.categoryName()).isEqualTo("Groceries");
            assertThat(r.groupingId()).isEqualTo(2L);
            assertThat(r.groupingName()).isEqualTo("Food");
            assertThat(r.appliedMs()).isEqualTo(1_700_000_000_000L);
            assertThat(r.appliedSeq()).isEqualTo(3L);
            assertThat(RecordedExpenseRowUtils.countByMessage(
                            jdbcTemplate, IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("when applied again with a new category and grouping - then the row holds them, staying "
                + "PROPOSED")
        void whenAppliedAgainWithNewCategoryAndGrouping_thenSameRowUpdatedStayingProposed() {
            long userId = 97002L;
            String messageId = "ri01-refile";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            long expenseId = 971002L;
            seedRow(
                    messageDbId,
                    userId,
                    expenseId,
                    "lunch",
                    null,
                    "12.50",
                    "EUR",
                    5L,
                    "Groceries",
                    2L,
                    "Food",
                    "PROPOSED",
                    1000L,
                    0L);

            SpendingRow refiled = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(9L, "Transport"),
                    Optional.of(new CategoryRef(4L, "Travel")));
            adapter.apply(refiled, RecordedStatus.PROPOSED, new StreamPosition(1001L, 0L));

            Optional<RecordedExpenseRow> result = rowFor(expenseId);
            assertThat(result).isPresent();
            RecordedExpenseRow r = result.orElseThrow();
            assertThat(r.status()).isEqualTo("PROPOSED");
            assertThat(r.categoryId()).isEqualTo(9L);
            assertThat(r.categoryName()).isEqualTo("Transport");
            assertThat(r.groupingId()).isEqualTo(4L);
            assertThat(r.groupingName()).isEqualTo("Travel");
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageDbId)).isEqualTo(1);
        }

        @Test
        @DisplayName("when applied at a newer position with ACCEPTED - then the same row is ACCEPTED, no second row")
        void whenAppliedAtNewerPositionAccepted_thenSameRowAcceptedNoSecondRow() {
            long userId = 97003L;
            String messageId = "ri01-accept";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            long expenseId = 971003L;
            seedRow(
                    messageDbId,
                    userId,
                    expenseId,
                    "lunch",
                    null,
                    "12.50",
                    "EUR",
                    5L,
                    "Groceries",
                    2L,
                    "Food",
                    "PROPOSED",
                    1000L,
                    0L);

            SpendingRow entry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(5L, "Groceries"),
                    Optional.of(new CategoryRef(2L, "Food")));
            adapter.apply(entry, RecordedStatus.ACCEPTED, new StreamPosition(1001L, 0L));

            Optional<RecordedExpenseRow> result = rowFor(expenseId);
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().status()).isEqualTo("ACCEPTED");
            assertThat(RecordedExpenseRowUtils.countByMessage(jdbcTemplate, messageDbId)).isEqualTo(1);
        }

        @Test
        @DisplayName("when applied at a newer position with DISCARDED - then the same row is DISCARDED")
        void whenAppliedAtNewerPositionDiscarded_thenSameRowDiscarded() {
            long userId = 97004L;
            String messageId = "ri01-discard";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            long expenseId = 971004L;
            seedRow(
                    messageDbId,
                    userId,
                    expenseId,
                    "lunch",
                    null,
                    "12.50",
                    "EUR",
                    5L,
                    "Groceries",
                    2L,
                    "Food",
                    "PROPOSED",
                    1000L,
                    0L);

            SpendingRow entry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(5L, "Groceries"),
                    Optional.of(new CategoryRef(2L, "Food")));
            adapter.apply(entry, RecordedStatus.DISCARDED, new StreamPosition(1001L, 0L));

            Optional<RecordedExpenseRow> result = rowFor(expenseId);
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().status()).isEqualTo("DISCARDED");
        }

        @Test
        @DisplayName("when three PROPOSED rows of one message are each accepted - then each is ACCEPTED, "
                + "nothing else changed")
        void whenThreeProposedRowsAcceptedIndividually_thenEachAcceptedNothingElseChanged() {
            long userId = 97005L;
            String messageId = "ri01-three";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            long expenseId1 = 971005L;
            long expenseId2 = 971006L;
            long expenseId3 = 971007L;
            seedRow(
                    messageDbId, userId, expenseId1, "a", null, "1.00", "EUR", 5L, "Groceries", 2L, "Food",
                    "PROPOSED", 1000L, 0L);
            seedRow(
                    messageDbId, userId, expenseId2, "b", null, "2.00", "EUR", 5L, "Groceries", 2L, "Food",
                    "PROPOSED", 1000L, 1L);
            seedRow(
                    messageDbId, userId, expenseId3, "c", null, "3.00", "EUR", 5L, "Groceries", 2L, "Food",
                    "PROPOSED", 1000L, 2L);

            for (long expenseId : List.of(expenseId1, expenseId2, expenseId3)) {
                SpendingRow entry = row(
                        expenseId,
                        userId,
                        Optional.of(messageId),
                        rowFor(expenseId).orElseThrow().description(),
                        Optional.empty(),
                        rowFor(expenseId).orElseThrow().amount(),
                        "EUR",
                        new CategoryRef(5L, "Groceries"),
                        Optional.of(new CategoryRef(2L, "Food")));
                adapter.apply(entry, RecordedStatus.ACCEPTED, new StreamPosition(2000L, 0L));
            }

            assertThat(rowFor(expenseId1).orElseThrow().status()).isEqualTo("ACCEPTED");
            assertThat(rowFor(expenseId2).orElseThrow().status()).isEqualTo("ACCEPTED");
            assertThat(rowFor(expenseId3).orElseThrow().status()).isEqualTo("ACCEPTED");
            assertThat(rowFor(expenseId1).orElseThrow().description()).isEqualTo("a");
            assertThat(rowFor(expenseId2).orElseThrow().description()).isEqualTo("b");
            assertThat(rowFor(expenseId3).orElseThrow().description()).isEqualTo("c");
        }

        @Test
        @DisplayName("when a registered message has no row and an ACCEPTED entry arrives - then the whole row "
                + "is inserted ACCEPTED")
        void whenRegisteredMessageHasNoRowAcceptedEntry_thenWholeRowInsertedAccepted() {
            long userId = 97006L;
            String messageId = "ri01-accept-first";
            registerMessage(userId, messageId);
            long expenseId = 971008L;
            SpendingRow entry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "taxi",
                    Optional.empty(),
                    "20.00",
                    "USD",
                    new CategoryRef(6L, "Transport"),
                    Optional.empty());

            adapter.apply(entry, RecordedStatus.ACCEPTED, new StreamPosition(1500L, 0L));

            Optional<RecordedExpenseRow> result = rowFor(expenseId);
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().status()).isEqualTo("ACCEPTED");
            assertThat(result.orElseThrow().description()).isEqualTo("taxi");
        }

        @Test
        @DisplayName("when applied at an older position with other content - then the row is untouched, throwing "
                + "nothing")
        void whenAppliedAtOlderPositionWithOtherContent_thenRowUntouchedThrowsNothing() {
            long userId = 97007L;
            String messageId = "ri01-older";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            long expenseId = 971009L;
            seedRow(
                    messageDbId, userId, expenseId, "lunch", null, "12.50", "EUR", 5L, "Groceries", 2L, "Food",
                    "PROPOSED", 1000L, 5L);

            SpendingRow olderEntry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "changed",
                    Optional.of("Changed Merchant"),
                    "99.99",
                    "USD",
                    new CategoryRef(1L, "Other"),
                    Optional.empty());

            assertThatCode(() -> adapter.apply(olderEntry, RecordedStatus.ACCEPTED, new StreamPosition(999L, 5L)))
                    .doesNotThrowAnyException();

            RecordedExpenseRow r = rowFor(expenseId).orElseThrow();
            assertThat(r.status()).isEqualTo("PROPOSED");
            assertThat(r.description()).isEqualTo("lunch");
            assertThat(r.amount()).isEqualTo("12.50");
            assertThat(r.currencyCode()).isEqualTo("EUR");
            assertThat(r.appliedMs()).isEqualTo(1000L);
            assertThat(r.appliedSeq()).isEqualTo(5L);
        }

        @Test
        @DisplayName("when applied again with the very same entry and position - then the row is unchanged, "
                + "throwing nothing")
        void whenAppliedAgainWithSameEntryAndPosition_thenRowUnchangedThrowsNothing() {
            long userId = 97008L;
            String messageId = "ri01-redelivery";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            long expenseId = 971010L;
            seedRow(
                    messageDbId, userId, expenseId, "lunch", null, "12.50", "EUR", 5L, "Groceries", 2L, "Food",
                    "PROPOSED", 1000L, 5L);

            SpendingRow sameEntry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(5L, "Groceries"),
                    Optional.of(new CategoryRef(2L, "Food")));

            assertThatCode(() -> adapter.apply(sameEntry, RecordedStatus.PROPOSED, new StreamPosition(1000L, 5L)))
                    .doesNotThrowAnyException();

            RecordedExpenseRow r = rowFor(expenseId).orElseThrow();
            assertThat(r.status()).isEqualTo("PROPOSED");
            assertThat(r.appliedMs()).isEqualTo(1000L);
            assertThat(r.appliedSeq()).isEqualTo(5L);
        }

        @Test
        @DisplayName("when a created and refiled PROPOSED entry replay in order - then the row ends with the "
                + "refiled category")
        void whenProposedAndRefileReplayedInOrder_thenRowEndsWithRefiledCategory() {
            long userId = 97009L;
            String messageId = "ri01-replay";
            registerMessage(userId, messageId);
            long expenseId = 971011L;
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            seedRow(
                    messageDbId, userId, expenseId, "lunch", null, "12.50", "EUR", 9L, "Transport", 4L, "Travel",
                    "PROPOSED", 2000L, 0L);

            SpendingRow created = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(5L, "Groceries"),
                    Optional.of(new CategoryRef(2L, "Food")));
            SpendingRow refiled = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(9L, "Transport"),
                    Optional.of(new CategoryRef(4L, "Travel")));

            adapter.apply(created, RecordedStatus.PROPOSED, new StreamPosition(3000L, 0L));
            adapter.apply(refiled, RecordedStatus.PROPOSED, new StreamPosition(3000L, 1L));

            RecordedExpenseRow r = rowFor(expenseId).orElseThrow();
            assertThat(r.categoryId()).isEqualTo(9L);
            assertThat(r.categoryName()).isEqualTo("Transport");
            assertThat(r.appliedMs()).isEqualTo(3000L);
            assertThat(r.appliedSeq()).isEqualTo(1L);
        }

        @Test
        @DisplayName("when no message is registered under the entry's identity - then no row is written, "
                + "throwing nothing")
        void whenNoMessageRegisteredUnderIdentity_thenNoRowWrittenThrowsNothing() {
            long userId = 97010L;
            String messageId = "ri01-unregistered";
            long expenseId = 971012L;
            SpendingRow entry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "lunch",
                    Optional.empty(),
                    "12.50",
                    "EUR",
                    new CategoryRef(5L, "Groceries"),
                    Optional.of(new CategoryRef(2L, "Food")));

            assertThatCode(() -> adapter.apply(entry, RecordedStatus.PROPOSED, new StreamPosition(1000L, 0L)))
                    .doesNotThrowAnyException();

            assertThat(rowFor(expenseId)).isEmpty();
        }

        @Test
        @DisplayName("when a registered message's entry has an empty grouping - then the category is held and "
                + "both grouping columns are null")
        void whenEntryGroupingEmpty_thenCategoryHeldGroupingColumnsNull() {
            long userId = 97011L;
            String messageId = "ri01-no-grouping";
            registerMessage(userId, messageId);
            long expenseId = 971013L;
            SpendingRow entry = row(
                    expenseId,
                    userId,
                    Optional.of(messageId),
                    "gift",
                    Optional.empty(),
                    "40.00",
                    "USD",
                    new CategoryRef(3L, "Gifts"),
                    Optional.empty());

            adapter.apply(entry, RecordedStatus.PROPOSED, new StreamPosition(1000L, 0L));

            RecordedExpenseRow r = rowFor(expenseId).orElseThrow();
            assertThat(r.categoryId()).isEqualTo(3L);
            assertThat(r.categoryName()).isEqualTo("Gifts");
            assertThat(r.groupingId()).isNull();
            assertThat(r.groupingName()).isNull();
        }
    }

    @Nested
    @DisplayName("applying a refile and an acceptance of one row concurrently")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class ApplyConcurrently {

        private static final int ITERATIONS = 20;

        @Test
        @DisplayName("when two threads repeatedly apply a refile and an acceptance - then the higher position "
                + "always wins")
        void whenTwoThreadsApplyRefileAndAcceptanceRepeatedly_thenEveryRunEndsWithHigherPositionContent()
                throws Exception {
            long userId = 97012L;
            String messageId = "ri01-concurrent";
            registerMessage(userId, messageId);
            long messageDbId = IncomingMessageRowUtils.id(jdbcTemplate, userId, messageId);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    long expenseId = 972000L + i;
                    long ms = 4000L + i;
                    seedRow(
                            messageDbId, userId, expenseId, "lunch", null, "12.50", "EUR", 5L, "Groceries", 2L,
                            "Food", "PROPOSED", ms, 0L);

                    SpendingRow refiled = row(
                            expenseId,
                            userId,
                            Optional.of(messageId),
                            "lunch",
                            Optional.empty(),
                            "12.50",
                            "EUR",
                            new CategoryRef(9L, "Transport"),
                            Optional.of(new CategoryRef(4L, "Travel")));
                    SpendingRow accepted = row(
                            expenseId,
                            userId,
                            Optional.of(messageId),
                            "lunch",
                            Optional.empty(),
                            "12.50",
                            "EUR",
                            new CategoryRef(7L, "Entertainment"),
                            Optional.of(new CategoryRef(3L, "Fun")));

                    Callable<Void> refileCall = () -> {
                        adapter.apply(refiled, RecordedStatus.PROPOSED, new StreamPosition(ms, 1L));
                        return null;
                    };
                    Callable<Void> acceptCall = () -> {
                        adapter.apply(accepted, RecordedStatus.ACCEPTED, new StreamPosition(ms, 2L));
                        return null;
                    };

                    List<Future<Void>> results =
                            executor.invokeAll(List.of(refileCall, acceptCall), 5, TimeUnit.SECONDS);
                    for (Future<Void> future : results) {
                        future.get();
                    }

                    RecordedExpenseRow r = rowFor(expenseId).orElseThrow();
                    assertThat(r.status()).isEqualTo("ACCEPTED");
                    assertThat(r.categoryId()).isEqualTo(7L);
                    assertThat(r.groupingId()).isEqualTo(3L);
                    assertThat(r.appliedMs()).isEqualTo(ms);
                    assertThat(r.appliedSeq()).isEqualTo(2L);
                }
            } finally {
                executor.shutdownNow();
                RecordedExpenseRowUtils.deleteAll(jdbcTemplate);
                IncomingMessageRowUtils.deleteByUser(jdbcTemplate, userId);
            }
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

        private final SpendingRow entry = new SpendingRow(
                1L,
                1L,
                Optional.of("mocked-entry"),
                "lunch",
                Optional.empty(),
                "12.50",
                CurrencyCode.of("EUR"),
                new CategoryRef(5L, "Groceries"),
                Optional.of(new CategoryRef(2L, "Food")));

        @Test
        @DisplayName("when the repository throws a resource-failure or transient exception - then throws "
                + "MessageStoreUnavailableException")
        void whenRepositoryThrowsResourceFailureOrTransientException_thenThrowsMessageStoreUnavailableException() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedRepository.upsertApplied(
                            anyLong(), any(), anyLong(), any(), any(), any(), any(), anyLong(), any(), any(), any(),
                            any(), anyLong(), anyLong(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.apply(entry, RecordedStatus.PROPOSED, new StreamPosition(1L, 0L)))
                    .isInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when the repository throws another DataAccessException - then throws MessageStoreFailedException, "
                        + "not its subtype")
        void whenRepositoryThrowsAnotherDataAccessException_thenThrowsMessageStoreFailedExceptionNotSubtype() {
            DataIntegrityViolationException frameworkException = new DataIntegrityViolationException("constraint");
            when(mockedRepository.upsertApplied(
                            anyLong(), any(), anyLong(), any(), any(), any(), any(), anyLong(), any(), any(), any(),
                            any(), anyLong(), anyLong(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.apply(entry, RecordedStatus.PROPOSED, new StreamPosition(1L, 0L)))
                    .isInstanceOf(MessageStoreFailedException.class)
                    .isNotInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }
}
