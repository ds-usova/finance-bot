package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.StoredGrouping;
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
@Import(GroupingRepositoryAdapter.class)
class GroupingRepositoryAdapterTest {

    @Autowired
    private GroupingRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("finding a grouping by user id and name")
    class FindByUserIdAndName {

        @Test
        @DisplayName(
                "when called with that user's id and the name of a stored parentless row - then answers a StoredGrouping carrying that row's id and name")
        void whenCalledForAStoredParentlessRow_thenAnswersStoredGroupingCarryingItsIdAndName() {
            long userId = storedUserId("utilities-grouping-user");
            long groupingId = storedGroupingId(userId, "Utilities");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(userId, "Utilities");

            assertThat(found).contains(new StoredGrouping(groupingId, "Utilities"));
        }

        @Test
        @DisplayName(
                "when called with that user's id and the name of a category under a grouping - then answers nothing, a category is not a grouping")
        void whenCalledForACategoryName_thenAnswersNothing() {
            long userId = storedUserId("supermarkets-category-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            storedCategoryId(userId, groupingId, "Supermarkets");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(userId, "Supermarkets");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with that user's id and a name carried both as a parentless row and as a category under another grouping - then answers the parentless row's id, not the category's")
        void whenNameIsBothAGroupingAndACategory_thenAnswersTheParentlessRow() {
            long userId = storedUserId("travel-dual-name-user");
            long travelGroupingId = storedGroupingId(userId, "Travel");
            long insuranceGroupingId = storedGroupingId(userId, "Insurance");
            storedCategoryId(userId, insuranceGroupingId, "Travel");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(userId, "Travel");

            assertThat(found).contains(new StoredGrouping(travelGroupingId, "Travel"));
        }

        @Test
        @DisplayName(
                "when called with the first of two users each owning a parentless row of the same name - then answers only that user's row")
        void whenTwoUsersShareAGroupingName_thenAnswersOnlyTheCallingUsersRow() {
            long firstUserId = storedUserId("first-grouping-owner-user");
            long secondUserId = storedUserId("second-grouping-owner-user");
            long firstGroupingId = storedGroupingId(firstUserId, "Shared Name");
            storedGroupingId(secondUserId, "Shared Name");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(firstUserId, "Shared Name");

            assertThat(found).contains(new StoredGrouping(firstGroupingId, "Shared Name"));
        }

        @Test
        @DisplayName("when called with a stored user with no row of that name - then answers nothing")
        void whenNoRowOfThatNameExists_thenAnswersNothing() {
            long userId = storedUserId("no-matching-grouping-user");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(userId, "Nonexistent");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a grouping's category names")
    class FindCategoryNames {

        @Test
        @DisplayName(
                "when called with that user's id and a grouping holding three categories stored out of alphabetical order - then answers the three names, sorted by name")
        void whenGroupingHoldsThreeCategoriesOutOfOrder_thenAnswersTheThreeNamesSorted() {
            long userId = storedUserId("three-categories-user");
            long groupingId = storedGroupingId(userId, "Entertainment");
            storedCategoryId(userId, groupingId, "Streaming");
            storedCategoryId(userId, groupingId, "Movies");
            storedCategoryId(userId, groupingId, "Concerts");
            StoredGrouping grouping = new StoredGrouping(groupingId, "Entertainment");

            List<String> names = adapter.findCategoryNames(userId, grouping);

            assertThat(names).containsExactly("Concerts", "Movies", "Streaming");
        }

        @Test
        @DisplayName("when called with that user's id and a grouping holding nothing - then answers an empty list")
        void whenGroupingHoldsNothing_thenAnswersEmptyList() {
            long userId = storedUserId("empty-grouping-user");
            long groupingId = storedGroupingId(userId, "Childless");
            StoredGrouping grouping = new StoredGrouping(groupingId, "Childless");

            List<String> names = adapter.findCategoryNames(userId, grouping);

            assertThat(names).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with the first user's id and the second user's grouping of the same name - then answers an empty list, the read is scoped to the caller")
        void whenCalledWithAnotherUsersGrouping_thenAnswersEmptyList() {
            long firstUserId = storedUserId("first-scoped-read-user");
            long secondUserId = storedUserId("second-scoped-read-user");
            storedGroupingId(firstUserId, "Shared Grouping Name");
            long secondGroupingId = storedGroupingId(secondUserId, "Shared Grouping Name");
            storedCategoryId(secondUserId, secondGroupingId, "Second User Category");
            StoredGrouping secondUsersGrouping = new StoredGrouping(secondGroupingId, "Shared Grouping Name");

            List<String> names = adapter.findCategoryNames(firstUserId, secondUsersGrouping);

            assertThat(names).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a user's grouping names that carry categories")
    class FindNamesWithCategories {

        @Test
        @DisplayName(
                "when called for a stored user with three groupings stored out of alphabetical order, each holding a category - then answers exactly the three grouping names, sorted, with no category name among them")
        void whenThreeGroupingsEachHoldACategory_thenAnswersTheThreeGroupingNamesSorted() {
            long userId = storedUserId("three-populated-groupings-user");
            long workId = storedGroupingId(userId, "Work");
            long homeId = storedGroupingId(userId, "Home");
            long autoId = storedGroupingId(userId, "Automotive");
            storedCategoryId(userId, workId, "Supplies");
            storedCategoryId(userId, homeId, "Furniture");
            storedCategoryId(userId, autoId, "Fuel");

            List<String> names = adapter.findNamesWithCategories(userId);

            assertThat(names).containsExactly("Automotive", "Home", "Work");
        }

        @Test
        @DisplayName(
                "when called for a stored user with one populated grouping and one holding nothing - then the empty grouping is absent")
        void whenOneGroupingIsEmpty_thenThatGroupingIsAbsent() {
            long userId = storedUserId("one-empty-one-populated-user");
            long populatedId = storedGroupingId(userId, "Populated Grouping");
            storedCategoryId(userId, populatedId, "A Category");
            storedGroupingId(userId, "Empty Grouping");

            List<String> names = adapter.findNamesWithCategories(userId);

            assertThat(names).containsExactly("Populated Grouping");
        }

        @Test
        @DisplayName(
                "when called for the first of two stored users each owning a populated grouping - then answers only that user's grouping name")
        void whenTwoUsersEachOwnAPopulatedGrouping_thenAnswersOnlyThatUsersGroupingName() {
            long firstUserId = storedUserId("first-populated-grouping-owner");
            long secondUserId = storedUserId("second-populated-grouping-owner");
            long firstGroupingId = storedGroupingId(firstUserId, "First User Grouping");
            long secondGroupingId = storedGroupingId(secondUserId, "Second User Grouping");
            storedCategoryId(firstUserId, firstGroupingId, "First User Category");
            storedCategoryId(secondUserId, secondGroupingId, "Second User Category");

            List<String> names = adapter.findNamesWithCategories(firstUserId);

            assertThat(names).containsExactly("First User Grouping");
        }

        @Test
        @DisplayName("when called for a stored user with no rows at all - then answers an empty list")
        void whenUserHasNoRowsAtAll_thenAnswersEmptyList() {
            long userId = storedUserId("no-rows-at-all-user");

            List<String> names = adapter.findNamesWithCategories(userId);

            assertThat(names).isEmpty();
        }
    }

    // These scenarios need a store that misbehaves in a way the healthy containerized Postgres
    // cannot be made to: an outright database failure. They construct their own adapter over a
    // Mockito mock and call the adapter's own public method directly - it is still the adapter
    // under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final CategoryEntityRepository mockedCategoryEntityRepository = mock(CategoryEntityRepository.class);
        private final GroupingRepositoryAdapter mockedAdapter =
                new GroupingRepositoryAdapter(mockedCategoryEntityRepository);

        @Test
        @DisplayName(
                "when findByUserIdAndName() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findByUserIdAndNameAndParentIdIsNull(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findByUserIdAndName(1L, "Groceries"))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findCategoryNames() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindCategoryNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findByUserIdAndParentIdOrderByName(any(), any()))
                    .thenThrow(frameworkException);
            StoredGrouping grouping = new StoredGrouping(1L, "Groceries");

            assertThatThrownBy(() -> mockedAdapter.findCategoryNames(1L, grouping))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findNamesWithCategories() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindNamesWithCategoriesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findNonEmptyGroupingNames(any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findNamesWithCategories(1L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedGroupingId(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    private long storedCategoryId(long userId, long parentId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, name);
    }
}
