package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
 * Exercises {@link ExpenseRepositoryAdapter#accept} across two real, concurrently-committing threads, the way
 * {@link UserRepositoryAdapterConcurrencyTest} exercises {@code create}: {@link PersistenceAdapterTest}'s
 * rolled-back-per-test transaction cannot show two threads racing on the same rows, since neither can see the
 * other's uncommitted work through it. {@code NOT_SUPPORTED} keeps the test method itself outside any
 * transaction, so each worker thread's call to {@code accept} opens and commits its own - and since rows then
 * survive the test, cleanup is manual.
 */
@PersistenceAdapterTest
@Import({ExpenseRepositoryAdapter.class, LedgerEventOutbox.class, SpendingEventRenderer.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExpenseRepositoryAdapterConcurrencyTest {

    @Autowired
    private ExpenseRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    private long userId;

    @AfterEach
    void cleanUp() {
        if (userId != 0) {
            ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId).forEach(jdbcAggregateTemplate::delete);
        }
    }

    @Nested
    @DisplayName("accepting entries concurrently")
    class Accept {

        @Test
        @DisplayName("when two threads race accept() on the same message - then only one succeeds and the rows "
                + "are RECORDED exactly once")
        void whenTwoThreadsRaceAcceptOnSameMessage_thenOneAnswersThreeOtherZeroAndRowsRecordedExactlyOnce()
                throws Exception {
            userId = UserRowUtils.storedUserId(userEntityRepository, "expense-accept-concurrency-user");
            long categoryId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Instant createdAt = Instant.now().minusSeconds(60);
            storedPendingExpense(categoryId, "First", 100, reference, createdAt);
            storedPendingExpense(categoryId, "Second", 200, reference, createdAt);
            storedPendingExpense(categoryId, "Third", 300, reference, createdAt);

            List<Future<Integer>> results = runConcurrently(
                    () -> adapter.accept(userId, reference, Instant.now()),
                    () -> adapter.accept(userId, reference, Instant.now()));

            int firstResult = results.get(0).get();
            int secondResult = results.get(1).get();

            assertThat(List.of(firstResult, secondResult)).containsExactlyInAnyOrder(3, 0);
            assertThat(ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, ExpenseStatus.RECORDED))
                    .hasSize(3);
        }
    }

    private void storedPendingExpense(
            long categoryId,
            String description,
            long amountMinorUnits,
            IncomingMessageId reference,
            Instant createdAt) {
        ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                amountMinorUnits,
                "USD",
                reference.value(),
                createdAt,
                ExpenseStatus.PENDING);
    }

    private List<Future<Integer>> runConcurrently(Callable<Integer> first, Callable<Integer> second)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> tasks = List.of(releasedTogether(latch, first), releasedTogether(latch, second));
            return executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Integer> releasedTogether(CountDownLatch latch, Callable<Integer> task) {
        return () -> {
            latch.countDown();
            latch.await();
            return task.call();
        };
    }
}
