package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.ai.common.boot.PersistenceAdapterTest;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapterTest
@Import(JdbcMessageStoreAdapter.class)
class JdbcMessageStoreAdapterTest {

    @Autowired
    private JdbcMessageStoreAdapter adapter;

    @Autowired
    private IncomingMessageEntityRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("registering an incoming message")
    class Register {

        @Test
        @DisplayName("when no row is stored under the identity - then one row holds the text with a received-at set")
        void whenNoRowUnderIdentity_thenOneRowHoldsTheTextWithReceivedAtSet() {
            MessageIdentity identity = new MessageIdentity(1001L, "register-new-message-id");

            adapter.register(identity, "hello there");

            IncomingMessageEntity stored = rowFor(identity.userId(), identity.incomingMessageId());
            assertThat(stored.text()).isEqualTo("hello there");
            assertThat(stored.receivedAt()).isNotNull();
        }

        @Test
        @DisplayName("when a row is already stored under the identity - then still one row keeping the first text")
        void whenRowAlreadyStoredUnderIdentity_thenStillOneRowKeepingFirstText() {
            MessageIdentity identity = new MessageIdentity(1002L, "register-duplicate-message-id");
            IncomingMessageRowUtils.insert(
                    jdbcTemplate, identity.userId(), identity.incomingMessageId(), "first text", Instant.now());

            adapter.register(identity, "second text");

            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, identity.userId(), identity.incomingMessageId()))
                    .isEqualTo(1);
            assertThat(IncomingMessageRowUtils.text(jdbcTemplate, identity.userId(), identity.incomingMessageId()))
                    .isEqualTo("first text");
        }

        @Test
        @DisplayName("when two users send the same incoming message id - then two rows exist, one per user")
        void whenTwoUsersSendSameIncomingMessageId_thenTwoRowsExistOnePerUser() {
            String sharedMessageId = "register-shared-message-id";
            MessageIdentity firstIdentity = new MessageIdentity(1003L, sharedMessageId);
            MessageIdentity secondIdentity = new MessageIdentity(1004L, sharedMessageId);

            adapter.register(firstIdentity, "text from first user");
            adapter.register(secondIdentity, "text from second user");

            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, firstIdentity.userId(), sharedMessageId))
                    .isEqualTo(1);
            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, secondIdentity.userId(), sharedMessageId))
                    .isEqualTo(1);
            assertThat(IncomingMessageRowUtils.text(jdbcTemplate, firstIdentity.userId(), sharedMessageId))
                    .isEqualTo("text from first user");
            assertThat(IncomingMessageRowUtils.text(jdbcTemplate, secondIdentity.userId(), sharedMessageId))
                    .isEqualTo("text from second user");
        }

        private IncomingMessageEntity rowFor(long userId, String incomingMessageId) {
            for (IncomingMessageEntity entity : repository.findAll()) {
                if (entity.userId() == userId && entity.incomingMessageId().equals(incomingMessageId)) {
                    return entity;
                }
            }
            throw new AssertionError("no row found for " + userId + "/" + incomingMessageId);
        }
    }

    @Nested
    @DisplayName("deleting rows received before a cut")
    class DeleteReceivedBefore {

        @Test
        @DisplayName("when rows are received before and after the cut - then answers the old count, deleting only "
                + "the old rows")
        void whenRowsReceivedBeforeAndAfterCut_thenAnswersOldCountDeletingOnlyOldRows() {
            Instant cut = Instant.now();
            Instant beforeCut = cut.minus(1, ChronoUnit.HOURS);
            Instant afterCut = cut.plus(1, ChronoUnit.HOURS);
            List<String> oldMessageIds = List.of("purge-old-message-1", "purge-old-message-2", "purge-old-message-3");
            List<String> youngMessageIds = List.of("purge-young-message-1", "purge-young-message-2");
            oldMessageIds.forEach(id -> IncomingMessageRowUtils.insert(jdbcTemplate, 2001L, id, "old text", beforeCut));
            youngMessageIds.forEach(
                    id -> IncomingMessageRowUtils.insert(jdbcTemplate, 2001L, id, "young text", afterCut));

            int deletedCount = adapter.deleteReceivedBefore(cut, 10);

            assertThat(deletedCount).isEqualTo(oldMessageIds.size());
            oldMessageIds.forEach(id -> assertThat(IncomingMessageRowUtils.count(jdbcTemplate, 2001L, id))
                    .isZero());
            youngMessageIds.forEach(id -> assertThat(IncomingMessageRowUtils.count(jdbcTemplate, 2001L, id))
                    .isEqualTo(1));
        }

        @Test
        @DisplayName(
                "when more rows are before the cut than one batch - then answers the batch size, deleting that many")
        void whenMoreRowsBeforeCutThanOneBatch_thenAnswersExactlyBatchSizeDeletingThatManyRows() {
            Instant cut = Instant.now();
            Instant beforeCut = cut.minus(1, ChronoUnit.HOURS);
            List<String> oldMessageIds = List.of(
                    "purge-batch-message-1",
                    "purge-batch-message-2",
                    "purge-batch-message-3",
                    "purge-batch-message-4",
                    "purge-batch-message-5");
            oldMessageIds.forEach(id -> IncomingMessageRowUtils.insert(jdbcTemplate, 2002L, id, "old text", beforeCut));

            int deletedCount = adapter.deleteReceivedBefore(cut, 3);

            assertThat(deletedCount).isEqualTo(3);
            long remaining = oldMessageIds.stream()
                    .filter(id -> IncomingMessageRowUtils.count(jdbcTemplate, 2002L, id) == 1)
                    .count();
            assertThat(remaining).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("purging concurrently")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class PurgeConcurrently {

        private static final long USER_ID = 2003L;
        private static final int BATCH_SIZE = 4;
        private static final int OLD_ROW_COUNT = 20;

        @Test
        @DisplayName("when two threads loop the purge until zero - then every old row is deleted exactly once "
                + "across both totals")
        void whenTwoThreadsLoopUntilZero_thenEveryOldRowGoneExactlyOnceCountsSumToTotal() throws Exception {
            Instant cut = Instant.now();
            Instant beforeCut = cut.minus(1, ChronoUnit.HOURS);
            List<String> oldMessageIds = new ArrayList<>();
            for (int i = 0; i < OLD_ROW_COUNT; i++) {
                String messageId = "purge-concurrent-message-" + i;
                oldMessageIds.add(messageId);
                IncomingMessageRowUtils.insert(jdbcTemplate, USER_ID, messageId, "old text", beforeCut);
            }

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> loopUntilZero = () -> {
                    int total = 0;
                    int deleted;
                    do {
                        deleted = adapter.deleteReceivedBefore(cut, BATCH_SIZE);
                        total += deleted;
                    } while (deleted > 0);
                    return total;
                };

                List<Future<Integer>> results =
                        executor.invokeAll(List.of(loopUntilZero, loopUntilZero), 5, TimeUnit.SECONDS);

                assertThat(results)
                        .allSatisfy(future -> assertThat(future.isCancelled()).isFalse());
                int firstTotal = results.get(0).get();
                int secondTotal = results.get(1).get();

                assertThat(firstTotal + secondTotal).isEqualTo(OLD_ROW_COUNT);
                assertThat(IncomingMessageRowUtils.countReceivedBefore(jdbcTemplate, USER_ID, cut))
                        .isZero();
            } finally {
                executor.shutdownNow();
                oldMessageIds.forEach(id -> jdbcTemplate.update(
                        "DELETE FROM incoming_message WHERE user_id = ? AND incoming_message_id = ?", USER_ID, id));
            }
        }
    }

    // The scenarios below need a store that fails in a way the healthy containerized Postgres cannot be
    // made to: a non-constraint failure on insert and on delete. Each constructs its own adapter over a
    // Mockito mock and calls the adapter's own public methods directly - it is still the adapter under
    // test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked repository, not the containerized database")
    class WithAMockedRepository {

        private final IncomingMessageEntityRepository mockedRepository = mock(IncomingMessageEntityRepository.class);
        private final JdbcMessageStoreAdapter mockedAdapter = new JdbcMessageStoreAdapter(mockedRepository);

        @Test
        @DisplayName("when the repository's insert throws a DataAccessException - then throws "
                + "MessageStoreFailedException wrapping it")
        void whenInsertThrowsDataAccessException_thenRegisterThrowsMessageStoreFailedExceptionWrappingIt() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedRepository.insertIgnoringConflict(anyLong(), anyString(), anyString()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.register(new MessageIdentity(1L, "mocked-insert-failure"), "text"))
                    .isInstanceOf(MessageStoreFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName("when the repository's delete throws a DataAccessException - then throws "
                + "MessageStoreFailedException wrapping it")
        void whenDeleteThrowsDataAccessException_thenDeleteReceivedBeforeThrowsMessageStoreFailedExceptionWrappingIt() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedRepository.deleteReceivedBefore(any(Instant.class), anyInt()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.deleteReceivedBefore(Instant.now(), 10))
                    .isInstanceOf(MessageStoreFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }
}
