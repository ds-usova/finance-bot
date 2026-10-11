package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.application.dto.WrittenMessageView;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.domain.exception.WrittenMessageInProgressException;
import bot.finance.domain.model.WrittenMessage;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises {@link WrittenMessageRepositoryAdapter} across two real, concurrently-committing threads, the way
 * {@link UserRepositoryAdapterConcurrencyTest} does: the rolled-back-per-test transaction of
 * {@link PersistenceAdapterTest} cannot show two threads racing, since neither sees the other's uncommitted work.
 * {@code NOT_SUPPORTED} keeps the test method outside any transaction, so each worker commits its own and
 * cleanup is manual.
 */
@PersistenceAdapterTest
@Import(WrittenMessageRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WrittenMessageRepositoryAdapterConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    private WrittenMessageRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private long userId;

    @AfterEach
    void cleanUp() {
        if (userId != 0) {
            userEntityRepository.deleteById(userId);
        }
    }

    @Nested
    @DisplayName("claiming the oldest waiting message concurrently")
    class ClaimOldestWaiting {

        @Test
        @DisplayName("when two threads claim at the same moment - then exactly one gets the message")
        void whenTwoThreadsClaimTogether_thenExactlyOneGetsTheMessage() throws Exception {
            userId = UserRowUtils.storedUserId(userEntityRepository, "written-claim-concurrency-user");
            long id = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "coffee", WrittenMessageStatus.WAITING, null, NOW);

            List<Future<Optional<WrittenMessageView>>> results = runConcurrently(
                    () -> adapter.claimOldestWaiting(Instant.now()), () -> adapter.claimOldestWaiting(Instant.now()));

            Optional<WrittenMessageView> first = results.get(0).get();
            Optional<WrittenMessageView> second = results.get(1).get();
            assertThat(List.of(first.isPresent(), second.isPresent())).containsExactlyInAnyOrder(true, false);
            assertThat(first.or(() -> second).orElseThrow().id()).isEqualTo(id);
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, id)
                            .status())
                    .isEqualTo("READING");
        }
    }

    @Nested
    @DisplayName("storing a message concurrently")
    class Store {

        @Test
        @DisplayName("when two threads store for one user at the same moment - then exactly one is stored")
        void whenTwoThreadsStoreForOneUser_thenExactlyOneIsStored() throws Exception {
            userId = UserRowUtils.storedUserId(userEntityRepository, "written-store-concurrency-user");

            List<Future<Optional<WrittenMessageView>>> results = runConcurrently(
                    () -> Optional.of(adapter.store(WrittenMessage.newMessage(userId, "first", Instant.now()))),
                    () -> Optional.of(adapter.store(WrittenMessage.newMessage(userId, "second", Instant.now()))));

            int stored = 0;
            int refused = 0;
            for (Future<Optional<WrittenMessageView>> result : results) {
                try {
                    result.get();
                    stored++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(WrittenMessageInProgressException.class);
                    refused++;
                }
            }
            assertThat(stored).isEqualTo(1);
            assertThat(refused).isEqualTo(1);
            assertThat(jdbcAggregateTemplate.findAll(WrittenMessageEntity.class).stream()
                            .filter(row -> row.userId() == userId))
                    .hasSize(1);
        }
    }

    private <T> List<Future<T>> runConcurrently(Callable<T> first, Callable<T> second) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<T>> tasks = List.of(releasedTogether(latch, first), releasedTogether(latch, second));
            return executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> Callable<T> releasedTogether(CountDownLatch latch, Callable<T> task) {
        return () -> {
            latch.countDown();
            latch.await();
            return task.call();
        };
    }
}
