package bot.finance.adapter.persistence;

import static org.mockito.Mockito.mock;

import bot.finance.common.CategoryRowUtils;
import bot.finance.common.PersistenceAdapterTest;
import bot.finance.common.UserRowUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
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
    @DisplayName("finding a category by grouping and name")
    class FindByGroupingAndName {

        @Test
        @DisplayName(
                "when called with a stored user with one grouping and one child category under it, and the child's name - then returns exactly that child, carrying its id, its name and its grouping's name as parentName")
        @Disabled("RI02: rework findByGroupingAndName() per the design")
        void whenCalledForAStoredChildCategory_thenReturnsItCarryingItsGroupingsNameAsParentName() {
            // long userId = storedUserId("child-category-user");
            // long groupingId = storedGroupingId(userId, "Groceries");
            // long childId = storedCategoryId(userId, groupingId, "Supermarket");
            //
            // List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Supermarket");
            //
            // assertThat(found).singleElement().satisfies(category -> {
            //     assertThat(category.id()).isEqualTo(childId);
            //     assertThat(category.name()).isEqualTo("Supermarket");
            //     assertThat(category.parentName()).contains("Groceries");
            // });
        }

        @Test
        @DisplayName(
                "when called with a stored user with a grouping, and the grouping's name - then returns exactly that grouping, its parentName empty")
        @Disabled("RI02: delete; a parentless row is a grouping, not a category under one")
        void whenCalledForAStoredGrouping_thenReturnsItWithEmptyParentName() {
            // long userId = storedUserId("grouping-category-user");
            // long groupingId = storedGroupingId(userId, "Utilities");
            //
            // List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Utilities");
            //
            // assertThat(found).singleElement().satisfies(category -> {
            //     assertThat(category.id()).isEqualTo(groupingId);
            //     assertThat(category.name()).isEqualTo("Utilities");
            //     assertThat(category.parentName()).isEmpty();
            // });
        }

        @Test
        @DisplayName(
                "when called with a stored user with two categories of the same name under two different groupings - then returns both, each carrying its own grouping's name")
        @Disabled("RI02: delete; replaced by the second findByGroupingAndName() scenario")
        void whenCalledForTwoCategoriesWithSameNameUnderDifferentGroupings_thenReturnsBothWithTheirOwnParentName() {
            // long userId = storedUserId("duplicate-name-category-user");
            // long firstGroupingId = storedGroupingId(userId, "Home");
            // long secondGroupingId = storedGroupingId(userId, "Work");
            // storedCategoryId(userId, firstGroupingId, "Supplies");
            // storedCategoryId(userId, secondGroupingId, "Supplies");
            //
            // List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Supplies");
            //
            // assertThat(found)
            //         .hasSize(2)
            //         .extracting(StoredCategory::parentName)
            //         .containsExactlyInAnyOrder(Optional.of("Home"), Optional.of("Work"));
        }

        @Test
        @DisplayName(
                "when called for one of two stored users each owning a category with the same name - then returns only that user's category")
        @Disabled("RI02: delete; replaced by the cross-user scenario in the design")
        void whenCalledForOneOfTwoUsersWithSameCategoryName_thenReturnsOnlyThatUsersCategory() {
            // long firstUserId = storedUserId("first-shared-name-user");
            // long secondUserId = storedUserId("second-shared-name-user");
            // long firstCategoryId = storedGroupingId(firstUserId, "Shared Name");
            // storedGroupingId(secondUserId, "Shared Name");
            //
            // List<StoredCategory> found = adapter.findByUserIdAndName(firstUserId, "Shared Name");
            //
            // assertThat(found).singleElement().satisfies(category -> {
            //     assertThat(category.id()).isEqualTo(firstCategoryId);
            // });
        }

        @Test
        @DisplayName("when called with a stored user with no category of that name - then returns an empty list")
        @Disabled("RI02: delete; replaced by the answers-nothing scenario")
        void whenNoCategoryOfThatNameExists_thenReturnsEmptyList() {
            // long userId = storedUserId("no-matching-category-user");
            //
            // List<StoredCategory> found = adapter.findByUserIdAndName(userId, "Nonexistent");
            //
            // assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a category's child names")
    class FindChildNames {

        @Test
        @DisplayName("when called for a stored grouping with three children - then returns the three names")
        @Disabled("RI02: delete; findChildNames moves to GroupingRepositoryAdapterTest (RI01)")
        void whenCalledForAGroupingWithThreeChildren_thenReturnsTheThreeNames() {
            // long userId = storedUserId("three-children-user");
            // long groupingId = storedGroupingId(userId, "Entertainment");
            // storedCategoryId(userId, groupingId, "Streaming");
            // storedCategoryId(userId, groupingId, "Movies");
            // storedCategoryId(userId, groupingId, "Concerts");
            //
            // List<String> names = adapter.findChildNames(groupingId);
            //
            // assertThat(names).containsExactly("Concerts", "Movies", "Streaming");
        }

        @Test
        @DisplayName("when called for a stored category with no children - then returns an empty list")
        @Disabled("RI02: delete, same reason")
        void whenCalledForACategoryWithNoChildren_thenReturnsEmptyList() {
            // long userId = storedUserId("no-children-user");
            // long categoryId = storedGroupingId(userId, "Childless");
            //
            // List<String> names = adapter.findChildNames(categoryId);
            //
            // assertThat(names).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a user's grouping names")
    class FindGroupingNames {

        @Test
        @DisplayName(
                "when called for a stored user with three groupings stored out of alphabetical order, each holding a child - then returns exactly the three grouping names, sorted by name, with no child name among them")
        @Disabled("RI02: delete; findGroupingNames moves to RI01")
        void whenCalledForAStoredUserWithThreeGroupingsEachHoldingAChild_thenReturnsTheThreeGroupingNamesSorted() {
            // long userId = storedUserId("three-groupings-user");
            // long workId = storedGroupingId(userId, "Work");
            // long homeId = storedGroupingId(userId, "Home");
            // long autoId = storedGroupingId(userId, "Automotive");
            // storedCategoryId(userId, workId, "Supplies");
            // storedCategoryId(userId, homeId, "Furniture");
            // storedCategoryId(userId, autoId, "Fuel");
            //
            // List<String> names = adapter.findGroupingNames(userId);
            //
            // assertThat(names).containsExactly("Automotive", "Home", "Work");
        }

        @Test
        @DisplayName(
                "when called for a stored user with a grouping that has no children - then that grouping is absent")
        @Disabled("RI02: delete, same reason")
        void whenCalledForAStoredUserWithAChildlessGrouping_thenThatGroupingIsAbsent() {
            // long userId = storedUserId("childless-grouping-user");
            // long populatedId = storedGroupingId(userId, "Populated Grouping");
            // storedCategoryId(userId, populatedId, "A Child");
            // storedGroupingId(userId, "Childless Grouping");
            //
            // List<String> names = adapter.findGroupingNames(userId);
            //
            // assertThat(names).containsExactly("Populated Grouping");
        }

        @Test
        @DisplayName(
                "when called for one of two stored users each owning a grouping - then only that user's grouping name is returned")
        @Disabled("RI02: delete, same reason")
        void whenCalledForOneOfTwoUsersEachOwningAGrouping_thenReturnsOnlyThatUsersGroupingName() {
            // long firstUserId = storedUserId("first-grouping-owner");
            // long secondUserId = storedUserId("second-grouping-owner");
            // long firstGroupingId = storedGroupingId(firstUserId, "First User Grouping");
            // long secondGroupingId = storedGroupingId(secondUserId, "Second User Grouping");
            // storedCategoryId(firstUserId, firstGroupingId, "First User Child");
            // storedCategoryId(secondUserId, secondGroupingId, "Second User Child");
            //
            // List<String> names = adapter.findGroupingNames(firstUserId);
            //
            // assertThat(names).containsExactly("First User Grouping");
        }

        @Test
        @DisplayName("when called for a stored user with no categories at all - then returns an empty list")
        @Disabled("RI02: delete, same reason")
        void whenCalledForAStoredUserWithNoCategories_thenReturnsEmptyList() {
            // long userId = storedUserId("no-categories-user");
            //
            // List<String> names = adapter.findGroupingNames(userId);
            //
            // assertThat(names).isEmpty();
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
        private final CategoryRepositoryAdapter mockedAdapter =
                new CategoryRepositoryAdapter(mockedCategoryEntityRepository);

        @Test
        @DisplayName(
                "when findByGroupingAndName() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        @Disabled("RI02: cover findByGroupingAndName() instead")
        void
                whenFindByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            // QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            // when(mockedCategoryEntityRepository.findByUserIdAndParentIdAndName(any(), any(), any()))
            //         .thenThrow(frameworkException);
            //
            // assertThatThrownBy(() -> mockedAdapter.findByGroupingAndName(1L, grouping, "Groceries"))
            //         .isInstanceOf(PersistenceFailedException.class)
            //         .extracting(Throwable::getCause)
            //         .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when existsByUserIdAndName() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        @Disabled("RI02: cover existsByUserIdAndName() instead")
        void
                whenFindChildNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            // QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            // when(mockedCategoryEntityRepository.existsByUserIdAndNameAndParentIdIsNotNull(any(), any()))
            //         .thenThrow(frameworkException);
            //
            // assertThatThrownBy(() -> mockedAdapter.existsByUserIdAndName(1L, "Groceries"))
            //         .isInstanceOf(PersistenceFailedException.class)
            //         .extracting(Throwable::getCause)
            //         .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findGroupingNames() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        @Disabled("RI02: delete; moves to RI01")
        void
                whenFindGroupingNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            // QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            // when(mockedCategoryEntityRepository.findNonEmptyGroupingNames(any())).thenThrow(frameworkException);
            //
            // assertThatThrownBy(() -> mockedAdapter.findGroupingNames(1L))
            //         .isInstanceOf(PersistenceFailedException.class)
            //         .extracting(Throwable::getCause)
            //         .isEqualTo(frameworkException);
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
