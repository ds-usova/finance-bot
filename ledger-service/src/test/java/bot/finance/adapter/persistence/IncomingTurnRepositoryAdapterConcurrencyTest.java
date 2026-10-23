package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.IncomingTurnRowUtils;
import bot.finance.common.rows.UserRowUtils;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
 * Exercises {@link IncomingTurnRepositoryAdapter} across two real, concurrently-committing threads, the way
 * {@link WrittenMessageRepositoryAdapterConcurrencyTest} does. {@code NOT_SUPPORTED} keeps the test method outside any
 * transaction, so each worker commits its own and cleanup is manual.
 */
@PersistenceAdapterTest
@Import(IncomingTurnRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IncomingTurnRepositoryAdapterConcurrencyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired
    private IncomingTurnRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private long userId;

    @AfterEach
    void cleanUp() {
        if (userId != 0) {
            IncomingTurnRowUtils.turnRowsFor(jdbcAggregateTemplate, userId)
                    .forEach(row -> jdbcAggregateTemplate.deleteById(row.id(), IncomingTurnEntity.class));
            userEntityRepository.deleteById(userId);
        }
    }

    @Nested
    @DisplayName("deleting a turn concurrently")
    class Delete {

        @Test
        @DisplayName("when two threads delete one turn at the same moment - then exactly one answers true")
        void whenTwoThreadsDeleteTogether_thenExactlyOneAnswersTrue() throws Exception {
            userId = UserRowUtils.storedUserId(userEntityRepository, "turn-delete-concurrency-user");
            long id = IncomingTurnRowUtils.storedTurnId(
                    jdbcAggregateTemplate, userId, "chat-race", "msg-race", "coffee", NOW, null, null);

            List<Future<Boolean>> results = runConcurrently(() -> adapter.delete(id), () -> adapter.delete(id));

            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, id)).isEmpty();
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
