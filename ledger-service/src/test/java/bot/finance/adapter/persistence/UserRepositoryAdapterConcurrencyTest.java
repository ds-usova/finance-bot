package bot.finance.adapter.persistence;

import bot.finance.common.CategoryRowUtils;
import bot.finance.common.PersistenceAdapterTest;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link UserRepositoryAdapter#create} across two real, concurrently-committing
 * threads. {@link PersistenceAdapterTest}'s rolled-back-per-test transaction cannot show this:
 * two threads can never observe each other's uncommitted work through it. {@code NOT_SUPPORTED}
 * keeps the test method itself outside any transaction, so each worker thread's call to
 * {@code create} opens and commits its own - and since rows then survive the test, cleanup is
 * manual.
 */
@PersistenceAdapterTest
@Import(UserRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserRepositoryAdapterConcurrencyTest {

    private static final String SAME_EXTERNAL_ID = "concurrency-test-same-external-id";
    private static final String EXTERNAL_ID_A = "concurrency-test-external-id-a";
    private static final String EXTERNAL_ID_B = "concurrency-test-external-id-b";
    private static final int CATEGORY_ROW_COUNT = 97;

    @Autowired
    private UserRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @AfterEach
    void cleanUp() {
        deleteIfStored(SAME_EXTERNAL_ID);
        deleteIfStored(EXTERNAL_ID_A);
        deleteIfStored(EXTERNAL_ID_B);
    }

    @Nested
    @DisplayName("creating a user concurrently")
    class Create {

        @Test
        @DisplayName("when two threads race to create a user under the same external id - then both calls return the same user, neither throws, and exactly one user row owning exactly 97 category rows exists afterwards")
        void whenTwoThreadsRaceOnTheSameExternalId_thenBothReturnTheSameUserAndOnlyOneRowSetIsWritten()
                throws Exception {
            List<Future<User>> results = runConcurrently(
                    () -> adapter.create(User.newUser(SAME_EXTERNAL_ID), Category.defaults()),
                    () -> adapter.create(User.newUser(SAME_EXTERNAL_ID), Category.defaults()));

            User firstResult = results.get(0).get();
            User secondResult = results.get(1).get();

            assertThat(firstResult).isEqualTo(secondResult);

            List<UserEntity> storedRows = userRowsFor(SAME_EXTERNAL_ID);
            assertThat(storedRows).hasSize(1);

            long userId = storedRows.get(0).id();
            assertThat(categoryRowsFor(userId)).hasSize(CATEGORY_ROW_COUNT);
        }

        @Test
        @DisplayName("when two threads race to create users under different external ids - then both users are stored, each owning its own 97 category rows")
        void whenTwoThreadsRaceOnDifferentExternalIds_thenBothUsersAreStoredEachOwningItsOwnCategoryRows()
                throws Exception {
            List<Future<User>> results = runConcurrently(
                    () -> adapter.create(User.newUser(EXTERNAL_ID_A), Category.defaults()),
                    () -> adapter.create(User.newUser(EXTERNAL_ID_B), Category.defaults()));

            User firstResult = results.get(0).get();
            User secondResult = results.get(1).get();

            assertThat(firstResult.externalId()).isEqualTo(EXTERNAL_ID_A);
            assertThat(secondResult.externalId()).isEqualTo(EXTERNAL_ID_B);

            long firstUserId = firstResult.id().orElseThrow();
            long secondUserId = secondResult.id().orElseThrow();

            List<CategoryEntity> firstUserRows = categoryRowsFor(firstUserId);
            List<CategoryEntity> secondUserRows = categoryRowsFor(secondUserId);
            assertThat(firstUserRows).hasSize(CATEGORY_ROW_COUNT);
            assertThat(secondUserRows).hasSize(CATEGORY_ROW_COUNT);

            List<Long> firstRowIds = firstUserRows.stream().map(CategoryEntity::id).toList();
            List<Long> secondRowIds = secondUserRows.stream().map(CategoryEntity::id).toList();
            assertThat(firstRowIds).doesNotContainAnyElementsOf(secondRowIds);
        }

    }

    private List<Future<User>> runConcurrently(Callable<User> first, Callable<User> second)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<User>> tasks = List.of(releasedTogether(latch, first), releasedTogether(latch, second));
            return executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<User> releasedTogether(CountDownLatch latch, Callable<User> task) {
        return () -> {
            latch.countDown();
            latch.await();
            return task.call();
        };
    }

    private void deleteIfStored(String externalId) {
        userEntityRepository.findByExternalId(externalId)
                .map(UserEntity::id)
                .ifPresent(userEntityRepository::deleteById);
    }

    private List<UserEntity> userRowsFor(String externalId) {
        return jdbcAggregateTemplate.findAll(UserEntity.class).stream()
                .filter(row -> row.externalId().equals(externalId))
                .toList();
    }

    private List<CategoryEntity> categoryRowsFor(long userId) {
        return CategoryRowUtils.categoryRowsFor(jdbcAggregateTemplate, userId);
    }

}
