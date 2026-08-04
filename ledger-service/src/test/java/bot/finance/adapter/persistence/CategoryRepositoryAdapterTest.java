package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.StoredCategory;
import bot.finance.common.CategoryRowUtils;
import bot.finance.common.PersistenceAdapterTest;
import bot.finance.common.UserRowUtils;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(CategoryRepositoryAdapter.class)
class CategoryRepositoryAdapterTest {

    @Autowired
    private CategoryRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("finding categories by user id and name")
    class FindByUserIdAndName {

        @Test
        @DisplayName(
                "when called with a stored user with one grouping and one child category under it, and the child's name - then returns exactly that child, carrying its id, its name and its grouping's name as parentName")
        void whenCalledForAStoredChildCategory_thenReturnsItCarryingItsGroupingsNameAsParentName() {
            long userId = storedUserId("child-category-user");
            long groupingId = storedCategoryId(userId, "Groceries");
            long childId = storedChildCategoryId(userId, groupingId, "Supermarket");

            List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Supermarket");

            assertThat(found).singleElement().satisfies(category -> {
                assertThat(category.id()).isEqualTo(childId);
                assertThat(category.name()).isEqualTo("Supermarket");
                assertThat(category.parentName()).contains("Groceries");
            });
        }

        @Test
        @DisplayName(
                "when called with a stored user with a grouping, and the grouping's name - then returns exactly that grouping, its parentName empty")
        void whenCalledForAStoredGrouping_thenReturnsItWithEmptyParentName() {
            long userId = storedUserId("grouping-category-user");
            long groupingId = storedCategoryId(userId, "Utilities");

            List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Utilities");

            assertThat(found).singleElement().satisfies(category -> {
                assertThat(category.id()).isEqualTo(groupingId);
                assertThat(category.name()).isEqualTo("Utilities");
                assertThat(category.parentName()).isEmpty();
            });
        }

        @Test
        @DisplayName(
                "when called with a stored user with two categories of the same name under two different groupings - then returns both, each carrying its own grouping's name")
        void whenCalledForTwoCategoriesWithSameNameUnderDifferentGroupings_thenReturnsBothWithTheirOwnParentName() {
            long userId = storedUserId("duplicate-name-category-user");
            long firstGroupingId = storedCategoryId(userId, "Home");
            long secondGroupingId = storedCategoryId(userId, "Work");
            storedChildCategoryId(userId, firstGroupingId, "Supplies");
            storedChildCategoryId(userId, secondGroupingId, "Supplies");

            List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Supplies");

            assertThat(found)
                    .hasSize(2)
                    .extracting(StoredCategory::parentName)
                    .containsExactlyInAnyOrder(Optional.of("Home"), Optional.of("Work"));
        }

        @Test
        @DisplayName(
                "when called for one of two stored users each owning a category with the same name - then returns only that user's category")
        void whenCalledForOneOfTwoUsersWithSameCategoryName_thenReturnsOnlyThatUsersCategory() {
            long firstUserId = storedUserId("first-shared-name-user");
            long secondUserId = storedUserId("second-shared-name-user");
            long firstCategoryId = storedCategoryId(firstUserId, "Shared Name");
            storedCategoryId(secondUserId, "Shared Name");

            List<StoredCategory> found = adapter.findByUserIdAndName(firstUserId, "Shared Name");

            assertThat(found).singleElement().satisfies(category -> {
                assertThat(category.id()).isEqualTo(firstCategoryId);
            });
        }

        @Test
        @DisplayName("when called with a stored user with no category of that name - then returns an empty list")
        void whenNoCategoryOfThatNameExists_thenReturnsEmptyList() {
            long userId = storedUserId("no-matching-category-user");

            List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a category's child names")
    class FindChildNames {

        @Test
        @DisplayName("when called for a stored grouping with three children - then returns the three names")
        void whenCalledForAGroupingWithThreeChildren_thenReturnsTheThreeNames() {
            long userId = storedUserId("three-children-user");
            long groupingId = storedCategoryId(userId, "Entertainment");
            storedChildCategoryId(userId, groupingId, "Movies");
            storedChildCategoryId(userId, groupingId, "Concerts");
            storedChildCategoryId(userId, groupingId, "Streaming");

            List<String> names = adapter.findChildNames(groupingId);

            assertThat(names).containsExactlyInAnyOrder("Movies", "Concerts", "Streaming");
        }

        @Test
        @DisplayName("when called for a stored category with no children - then returns an empty list")
        void whenCalledForACategoryWithNoChildren_thenReturnsEmptyList() {
            long userId = storedUserId("no-children-user");
            long categoryId = storedCategoryId(userId, "Childless");

            List<String> names = adapter.findChildNames(categoryId);

            assertThat(names).isEmpty();
        }
    }

    // TODO RI01: this whole nested class is replaced by findGroupingNames()'s own nested class — see plan.md
    // RI01 for its scenarios (stored groupings sorted by name, a childless grouping present, scoped per user, an
    // empty catalogue).

    // These scenarios need a store that misbehaves in a way the healthy containerized Postgres
    // cannot be made to: an outright database failure. They construct their own adapter over a
    // Mockito mock and call the adapter's own public method directly - it is still the adapter
    // under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final CategoryEntityRepository mockedCategoryEntityRepository = mock(CategoryEntityRepository.class);
        private final CategoryRepositoryAdapter mockedAdapter =
                new CategoryRepositoryAdapter(mockedCategoryEntityRepository);

        @Test
        @DisplayName(
                "when findByUserIdAndName() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findByUserIdAndName(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findByUserIdAndName(1L, "Groceries"))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findChildNames() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindChildNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findByParentIdOrderByName(any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findChildNames(1L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        // TODO RI01: replace with the mocked-store scenario for findGroupingNames(), stubbing the new query
        // method.
        @Test
        @DisplayName(
                "when findGroupingNames() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindGroupingNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findGroupingNames(any())).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findGroupingNames(1L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedCategoryId(long userId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, name);
    }

    private long storedChildCategoryId(long userId, long parentId, String name) {
        return CategoryRowUtils.storedChildCategoryId(jdbcAggregateTemplate, userId, parentId, name);
    }
}
