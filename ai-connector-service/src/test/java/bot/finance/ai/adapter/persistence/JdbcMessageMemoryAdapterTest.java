package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.ai.adapter.scheduling.MemoryConfiguration;
import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.common.boot.PersistenceAdapterTest;
import bot.finance.ai.common.fixtures.EmbeddingFixtures;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
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
@Import({JdbcMessageMemoryAdapter.class, MemoryConfiguration.class})
class JdbcMessageMemoryAdapterTest {

    @Autowired
    private JdbcMessageMemoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long insertMessageWithVector(
            long userId, String incomingMessageId, String text, Instant receivedAt, List<Float> vector) {
        return IncomingMessageRowUtils.insertWithVectorReturningId(
                jdbcTemplate, userId, incomingMessageId, text, receivedAt, vector, 0);
    }

    private long insertMessageNoVector(long userId, String incomingMessageId, String text, Instant receivedAt) {
        return IncomingMessageRowUtils.insertReturningId(jdbcTemplate, userId, incomingMessageId, text, receivedAt);
    }

    private long insertMessageWithAttempts(
            long userId, String incomingMessageId, String text, Instant receivedAt, int attempts) {
        return IncomingMessageRowUtils.insertWithAttemptsReturningId(
                jdbcTemplate, userId, incomingMessageId, text, receivedAt, attempts);
    }

    private long idFor(long userId, String incomingMessageId) {
        return IncomingMessageRowUtils.id(jdbcTemplate, userId, incomingMessageId);
    }

    private void setClaimedAt(long messageId, Instant claimedAt) {
        IncomingMessageRowUtils.setBackfillClaimedAt(jdbcTemplate, messageId, claimedAt);
    }

    // FindExamples's tests are rewritten against RecordedExpenseRowUtils.insertApplied(...) - a decided row
    // is now keyed by expenseId, stores its amount as a decimal string and holds a groupingId, and UNKNOWN is
    // no longer a status the table accepts.

    private ExampleQuery query(
            long userId,
            long excludeId,
            List<Float> embedding,
            int examples,
            double minSimilarity,
            Duration recentWindow,
            Duration maxAge,
            int exampleLines) {
        return new ExampleQuery(
                userId,
                excludeId,
                new Embedding(embedding),
                examples,
                minSimilarity,
                recentWindow,
                maxAge,
                exampleLines);
    }

    private ExampleQuery defaultQuery(long userId, long excludeId, List<Float> embedding) {
        return query(userId, excludeId, embedding, 3, 0.6, Duration.ofDays(30), Duration.ofDays(365), 10);
    }

    @Nested
    @DisplayName("finding a registered message")
    class Find {

        @Test
        @DisplayName("when a row holds a vector - then answers its id and the vector, component for component")
        void whenRowHoldsVector_thenAnswersIdAndVectorComponentForComponent() {
            List<Float> vector = EmbeddingFixtures.unitVector(0);
            long id = insertMessageWithVector(9201L, "find-with-vector", "text", Instant.now(), vector);

            Optional<RegisteredMessage> result = adapter.find(new MessageIdentity(9201L, "find-with-vector"));

            assertThat(result).isPresent();
            assertThat(result.get().messageId()).isEqualTo(id);
            assertThat(result.get().embedding()).isPresent();
            assertThat(result.get().embedding().get().values()).isEqualTo(vector);
        }

        @Test
        @DisplayName("when a row holds no vector - then answers its id and no vector")
        void whenRowHoldsNoVector_thenAnswersIdAndNoVector() {
            long id = insertMessageNoVector(9202L, "find-no-vector", "text", Instant.now());

            Optional<RegisteredMessage> result = adapter.find(new MessageIdentity(9202L, "find-no-vector"));

            assertThat(result).isPresent();
            assertThat(result.get().messageId()).isEqualTo(id);
            assertThat(result.get().embedding()).isEmpty();
        }

        @Test
        @DisplayName("when no row exists under the identity, only under another person's - then answers nothing")
        void whenNoRowUnderIdentityOnlyUnderAnotherPerson_thenAnswersNothing() {
            insertMessageNoVector(9203L, "find-cross-user", "text", Instant.now());

            Optional<RegisteredMessage> result = adapter.find(new MessageIdentity(9204L, "find-cross-user"));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("storing an embedding")
    class StoreEmbedding {

        @Test
        @DisplayName("when a row holds no vector and a backfill claim - then it holds the vector and no claim")
        void whenRowHoldsNoVectorAndClaim_thenHoldsVectorAndNoClaim() {
            long id = insertMessageNoVector(9301L, "store-embedding-claimed", "text", Instant.now());
            setClaimedAt(id, Instant.now());
            List<Float> vector = EmbeddingFixtures.unitVector(1);

            adapter.storeEmbedding(id, new Embedding(vector));

            assertThat(IncomingMessageRowUtils.vector(jdbcTemplate, 9301L, "store-embedding-claimed"))
                    .isEqualTo(vector);
            assertThat(IncomingMessageRowUtils.backfillClaimedAt(jdbcTemplate, 9301L, "store-embedding-claimed"))
                    .isNull();
        }

        @Test
        @DisplayName("when a row already holds a vector - then storing another leaves the stored vector unchanged")
        void whenRowAlreadyHoldsVector_thenStoringAnotherLeavesStoredVectorUnchanged() {
            List<Float> originalVector = EmbeddingFixtures.unitVector(2);
            long id = insertMessageWithVector(
                    9302L, "store-embedding-already-set", "text", Instant.now(), originalVector);

            adapter.storeEmbedding(id, new Embedding(EmbeddingFixtures.unitVector(3)));

            assertThat(IncomingMessageRowUtils.vector(jdbcTemplate, 9302L, "store-embedding-already-set"))
                    .isEqualTo(originalVector);
        }
    }

    @Nested
    @DisplayName("counting an embedding attempt")
    class CountEmbeddingAttempt {

        @Test
        @DisplayName("when called twice - then answers one then two, releasing the claim, another row untouched")
        void whenCalledTwice_thenAnswersOneThenTwoReleasingClaimAnotherRowUntouched() {
            long claimedId = insertMessageNoVector(9401L, "count-attempt-claimed", "text", Instant.now());
            setClaimedAt(claimedId, Instant.now());
            insertMessageNoVector(9402L, "count-attempt-other", "text", Instant.now());

            int firstCount = adapter.countEmbeddingAttempt(claimedId);
            int secondCount = adapter.countEmbeddingAttempt(claimedId);

            assertThat(firstCount).isEqualTo(1);
            assertThat(secondCount).isEqualTo(2);
            assertThat(IncomingMessageRowUtils.embeddingAttempts(jdbcTemplate, 9401L, "count-attempt-claimed"))
                    .isEqualTo(2);
            assertThat(IncomingMessageRowUtils.backfillClaimedAt(jdbcTemplate, 9401L, "count-attempt-claimed"))
                    .isNull();
            assertThat(IncomingMessageRowUtils.embeddingAttempts(jdbcTemplate, 9402L, "count-attempt-other"))
                    .isZero();
        }
    }

    // This whole nested class is rewritten against RecordedExpenseRowUtils.insertApplied(...) - a decided
    // row is now keyed by expenseId, stores its amount as a decimal string and holds a groupingId, and
    // UNKNOWN is no longer a status the table accepts.
    @Nested
    @DisplayName("finding examples")
    class FindExamples {

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when three clear the bar and a fourth does not - then answers the three, closest first")
        void whenThreeClearBarAndFourthDoesNot_thenAnswersThreeClosestFirst() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when the three closest are old and a fourth clears the recent window - then the recent is last")
        void whenThreeClosestAreOldAndFourthClearsRecentWindow_thenRecentIsLast() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when the closest recent message is already among the closest - then it appears once")
        void whenClosestRecentAlreadyAmongClosest_thenAppearsOnce() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when no message clears the minimum similarity - then answers nothing")
        void whenNoMessageClearsMinimumSimilarity_thenAnswersNothing() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when the query is the message being handled itself - then it is not among the examples")
        void whenQueryIsMessageBeingHandledItself_thenNotAmongExamples() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when a message is before max age or belongs to another person - then neither is an example")
        void whenMessageBeforeMaxAgeOrAnotherPerson_thenNeitherIsExample() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when a message is all PROPOSED or holds no expense - then neither is an example")
        void whenMessageAllProposedOrNoExpense_thenNeitherIsExample() {}

        @Disabled("RI02: rewritten to a neighbour holding ACCEPTED, DISCARDED and PROPOSED rows - UNKNOWN is gone")
        @Test
        @DisplayName("when a neighbour holds an accepted, a discarded and an unknown expense - then only the "
                + "decided two are carried")
        void whenNeighbourHoldsAcceptedDiscardedAndUnknown_thenOnlyDecidedTwoAreCarried() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when a neighbour holds more decided expenses than the bound - then only the first that many")
        void whenNeighbourHoldsMoreDecidedExpensesThanBound_thenOnlyFirstThatMany() {}

        @Disabled("RI02: rewritten to a row with a category name and no grouping, asserting only grouping is absent")
        @Test
        @DisplayName(
                "when a neighbour's expense has no category or grouping name - then both are absent, rest unchanged")
        void whenExpenseHasNoCategoryOrGroupingName_thenBothAbsentRestUnchanged() {}

        @Disabled("RI02: deleted - the amount is no longer stored in minor units to be scaled on the way out")
        @Test
        @DisplayName("when amounts are 1550 EUR and 1500 JPY - then they read as 15.50 and 1500, beside their currency")
        void whenAmountsAre1550EurAnd1500Jpy_thenReadAsMainUnitDecimalBesideOwnCurrency() {}

        @Disabled("RI02: rewritten against RecordedExpenseRowUtils.insertApplied(...)")
        @Test
        @DisplayName("when a neighbour holds no vector - then it is not among the examples")
        void whenNeighbourHoldsNoVector_thenNotAmongExamples() {}
    }

    @Nested
    @DisplayName("claiming unembedded rows")
    class ClaimUnembedded {

        @Test
        @DisplayName("when rows differ by claim freshness and attempt count - then answers the claimable, ascending")
        void whenRowsDifferByClaimFreshnessAndAttemptCount_thenAnswersClaimableAscending() {
            Instant now = Instant.now();
            long plainA = insertMessageNoVector(9601L, "claim-plain-a", "text a", now);
            long plainB = insertMessageNoVector(9601L, "claim-plain-b", "text b", now);
            long freshClaim = insertMessageNoVector(9601L, "claim-fresh", "text fresh", now);
            setClaimedAt(freshClaim, now);
            long staleClaim = insertMessageNoVector(9601L, "claim-stale", "text stale", now);
            setClaimedAt(staleClaim, now.minus(Duration.ofMinutes(20)));
            long atAttemptBound = insertMessageWithAttempts(9601L, "claim-exhausted", "text exhausted", now, 3);

            List<UnembeddedMessage> result = adapter.claimUnembedded(10, 3, Duration.ofMinutes(10));

            assertThat(result).extracting(UnembeddedMessage::messageId).containsExactly(plainA, plainB, staleClaim);
            assertThat(result).extracting(UnembeddedMessage::text).containsExactly("text a", "text b", "text stale");
            assertThat(IncomingMessageRowUtils.backfillClaimedAt(jdbcTemplate, 9601L, "claim-plain-a"))
                    .isNotNull();
            assertThat(IncomingMessageRowUtils.backfillClaimedAt(jdbcTemplate, 9601L, "claim-plain-b"))
                    .isNotNull();
            assertThat(IncomingMessageRowUtils.backfillClaimedAt(jdbcTemplate, 9601L, "claim-stale"))
                    .isNotNull();
            assertThat(freshClaim)
                    .isNotIn(result.stream().map(UnembeddedMessage::messageId).toList());
            assertThat(atAttemptBound)
                    .isNotIn(result.stream().map(UnembeddedMessage::messageId).toList());
        }

        @Test
        @DisplayName("when more rows are claimable than the batch - then answers exactly the batch, lowest ids first")
        void whenMoreRowsClaimableThanBatch_thenAnswersExactlyBatchLowestIdsFirst() {
            Instant now = Instant.now();
            long first = insertMessageNoVector(9602L, "claim-batch-1", "t1", now);
            long second = insertMessageNoVector(9602L, "claim-batch-2", "t2", now);
            insertMessageNoVector(9602L, "claim-batch-3", "t3", now);
            insertMessageNoVector(9602L, "claim-batch-4", "t4", now);

            List<UnembeddedMessage> result = adapter.claimUnembedded(2, 3, Duration.ofMinutes(10));

            assertThat(result).extracting(UnembeddedMessage::messageId).containsExactly(first, second);
        }

        @Test
        @DisplayName("when a row holds a vector - then it is never claimed")
        void whenRowHoldsVector_thenNeverClaimed() {
            long id = insertMessageWithVector(
                    9603L, "claim-with-vector", "text", Instant.now(), EmbeddingFixtures.unitVector(0));

            List<UnembeddedMessage> result = adapter.claimUnembedded(10, 3, Duration.ofMinutes(10));

            assertThat(result).extracting(UnembeddedMessage::messageId).doesNotContain(id);
            assertThat(IncomingMessageRowUtils.backfillClaimedAt(jdbcTemplate, 9603L, "claim-with-vector"))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("claiming unembedded rows concurrently")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class ClaimUnembeddedConcurrently {

        private static final long USER_ID = 9604L;
        private static final int BATCH_SIZE = 4;
        private static final int ROW_COUNT = 20;

        @Test
        @DisplayName("when two threads claim repeatedly over fresh rows - then no row is answered to both")
        void whenTwoThreadsClaimRepeatedlyOverFreshRows_thenNoRowAnsweredToBoth() throws Exception {
            for (int i = 0; i < ROW_COUNT; i++) {
                IncomingMessageRowUtils.insert(
                        jdbcTemplate, USER_ID, "claim-concurrent-message-" + i, "text " + i, Instant.now());
            }

            Set<Long> claimedIds = ConcurrentHashMap.newKeySet();
            AtomicInteger duplicates = new AtomicInteger();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> loopUntilEmpty = () -> {
                    int total = 0;
                    List<UnembeddedMessage> claimed;
                    do {
                        claimed = adapter.claimUnembedded(BATCH_SIZE, 3, Duration.ofMinutes(10));
                        for (UnembeddedMessage message : claimed) {
                            if (!claimedIds.add(message.messageId())) {
                                duplicates.incrementAndGet();
                            }
                        }
                        total += claimed.size();
                    } while (!claimed.isEmpty());
                    return total;
                };

                List<Future<Integer>> results =
                        executor.invokeAll(List.of(loopUntilEmpty, loopUntilEmpty), 5, TimeUnit.SECONDS);

                assertThat(results)
                        .allSatisfy(future -> assertThat(future.isCancelled()).isFalse());
                int firstTotal = results.get(0).get();
                int secondTotal = results.get(1).get();

                assertThat(duplicates.get()).isZero();
                assertThat(firstTotal + secondTotal).isEqualTo(ROW_COUNT);
                assertThat(claimedIds).hasSize(ROW_COUNT);
            } finally {
                executor.shutdownNow();
                IncomingMessageRowUtils.deleteByUser(jdbcTemplate, USER_ID);
            }
        }
    }

    // The scenario below needs a store that fails in a way the healthy containerized Postgres cannot be made
    // to. It constructs its own adapter over a Mockito mock and calls the adapter's own public methods directly -
    // it is still the adapter under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked repository, not the containerized database")
    class WithAMockedRepository {

        private final IncomingMessageEntityRepository mockedMessageRepository =
                mock(IncomingMessageEntityRepository.class);
        private final RecordedExpenseEntityRepository mockedExpenseRepository =
                mock(RecordedExpenseEntityRepository.class);
        private final JdbcMessageMemoryAdapter mockedAdapter =
                new JdbcMessageMemoryAdapter(mockedMessageRepository, mockedExpenseRepository, Clock.systemUTC());

        @Test
        @DisplayName(
                "when the repository throws a resource-failure exception - then find() throws MessageStoreUnavailableException")
        void whenRepositoryThrowsResourceFailureException_thenFindThrowsMessageStoreUnavailableExceptionWrappingIt() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedMessageRepository.findEmbeddingRowByIdentity(anyLong(), anyString()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.find(new MessageIdentity(1L, "mocked-find-failure")))
                    .isInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when the repository throws another DataAccessException - then find() throws MessageStoreFailedException")
        void whenRepositoryThrowsAnotherDataAccessException_thenFindThrowsMessageStoreFailedExceptionNotSubtype() {
            DataIntegrityViolationException frameworkException =
                    new DataIntegrityViolationException("constraint violated");
            when(mockedMessageRepository.findEmbeddingRowByIdentity(anyLong(), anyString()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.find(new MessageIdentity(2L, "mocked-find-failure-2")))
                    .isInstanceOf(MessageStoreFailedException.class)
                    .isNotInstanceOf(MessageStoreUnavailableException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }
}
