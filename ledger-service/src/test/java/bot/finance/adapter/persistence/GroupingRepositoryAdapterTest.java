package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.GroupingEntry;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.UserRowUtils;
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
                "when the name given is a stored parentless row - then answers a StoredGrouping with its id and name")
        void whenCalledForAStoredParentlessRow_thenAnswersStoredGroupingWithItsIdAndName() {
            long userId = storedUserId("utilities-grouping-user");
            long groupingId = storedGroupingId(userId, "Utilities");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(userId, "Utilities");

            assertThat(found).contains(new StoredGrouping(groupingId, "Utilities"));
        }

        @Test
        @DisplayName(
                "when the name given is a category under a grouping - then answers nothing, a category is not a grouping")
        void whenCalledForACategoryName_thenAnswersNothing() {
            long userId = storedUserId("supermarkets-category-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            storedCategoryId(userId, groupingId, "Supermarkets");

            Optional<StoredGrouping> found = adapter.findByUserIdAndName(userId, "Supermarkets");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName(
                "when a name is carried both as a parentless row and as a category - then answers the parentless row")
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
                "when two users each own a parentless row of the same name - then answers only the calling user's row")
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
                "when a grouping holds three categories stored out of alphabetical order - then answers the three names sorted")
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
                "when the grouping given belongs to another user - then answers an empty list, the read is scoped to the caller")
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
                "when three groupings each hold a category, stored out of alphabetical order - then answers the three names sorted")
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
        @DisplayName("when one grouping is populated and one holds nothing - then the empty grouping is absent")
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
                "when two users each own a populated grouping - then answers only the calling user's grouping name")
        void whenTwoUsersEachOwnAPopulatedGrouping_thenAnswersOnlyTheCallingUsersGroupingName() {
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

    @Nested
    @DisplayName("finding all of a user's groupings")
    class FindAllForUser {

        @Test
        @DisplayName(
                "when a user's groupings were seeded - then every one comes back, unpaged, carrying its id and name")
        void whenGroupingsWereSeeded_thenEveryOneComesBackCarryingIdAndName() {
            long userId = storedUserId("find-all-groupings-default-tree-user");
            long homeId = storedGroupingId(userId, "Home");
            long workId = storedGroupingId(userId, "Work");
            storedCategoryId(userId, homeId, "Rent");
            storedCategoryId(userId, workId, "Supplies");

            List<GroupingEntry> groupings = adapter.findAllForUser(userId);

            assertThat(groupings)
                    .containsExactlyInAnyOrder(new GroupingEntry(homeId, "Home"), new GroupingEntry(workId, "Work"));
        }

        @Test
        @DisplayName("when a grouping holds no categories - then it comes back too, unlike findNamesWithCategories()")
        void whenGroupingHoldsNoCategories_thenThatGroupingComesBackToo() {
            long userId = storedUserId("find-all-groupings-empty-grouping-user");
            long populatedId = storedGroupingId(userId, "Populated");
            storedCategoryId(userId, populatedId, "Category");
            long emptyId = storedGroupingId(userId, "Empty");

            List<GroupingEntry> groupings = adapter.findAllForUser(userId);

            assertThat(groupings)
                    .containsExactlyInAnyOrder(
                            new GroupingEntry(populatedId, "Populated"), new GroupingEntry(emptyId, "Empty"));
        }

        @Test
        @DisplayName("when another user owns groupings too - then none of theirs appears")
        void whenCalledForFirstOfTwoUsers_thenNoneOfSecondUsersGroupingsAppears() {
            long firstUserId = storedUserId("find-all-groupings-first-user");
            long secondUserId = storedUserId("find-all-groupings-second-user");
            long firstGroupingId = storedGroupingId(firstUserId, "First Grouping");
            storedGroupingId(secondUserId, "Second Grouping");

            List<GroupingEntry> groupings = adapter.findAllForUser(firstUserId);

            assertThat(groupings).containsExactly(new GroupingEntry(firstGroupingId, "First Grouping"));
        }

        @Test
        @DisplayName("when called for a user with no rows at all - then an empty list comes back rather than null")
        void whenUserHasNoRowsAtAll_thenEmptyListComesBackRatherThanNull() {
            long userId = storedUserId("find-all-groupings-no-rows-user");

            List<GroupingEntry> groupings = adapter.findAllForUser(userId);

            assertThat(groupings).isNotNull().isEmpty();
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
                "when findByUserIdAndName() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
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
                "when findCategoryNames() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindCategoryNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
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
                "when findNamesWithCategories() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindNamesWithCategoriesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findNonEmptyGroupingNames(any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findNamesWithCategories(1L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        // A default answer that throws for any call, rather than a stub on one method, so the
        // scenario stays about the failure surfacing and not about which query the adapter runs.
        @Test
        @DisplayName(
                "when findAllForUser() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindAllForUserHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            CategoryEntityRepository throwingRepository = mock(CategoryEntityRepository.class, invocation -> {
                throw frameworkException;
            });
            GroupingRepositoryAdapter throwingAdapter = new GroupingRepositoryAdapter(throwingRepository);

            assertThatThrownBy(() -> throwingAdapter.findAllForUser(1L))
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
