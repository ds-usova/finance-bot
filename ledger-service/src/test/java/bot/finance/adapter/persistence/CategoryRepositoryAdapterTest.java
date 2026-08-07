package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.dto.StoredCategory;
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
                "when called with a stored user's id, a grouping and the name of a category stored under it - then answers a StoredCategory carrying that row's id and name")
        void whenCalledForAStoredCategoryUnderThatGrouping_thenAnswersStoredCategoryCarryingIdAndName() {
            long userId = storedUserId("supermarkets-category-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            long categoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            StoredGrouping grouping = new StoredGrouping(groupingId, "Groceries");

            Optional<StoredCategory> found = adapter.findByGroupingAndName(userId, grouping, "Supermarkets");

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(categoryId);
            assertThat(found.get().name()).isEqualTo("Supermarkets");
        }

        @Test
        @DisplayName(
                "when called with the first of two groupings holding a category of the same name and that name - then answers the row filed under the first grouping")
        void whenCalledForFirstOfTwoGroupingsSharingACategoryName_thenAnswersRowFiledUnderFirstGrouping() {
            long userId = storedUserId("shared-category-name-user");
            long firstGroupingId = storedGroupingId(userId, "Home");
            long secondGroupingId = storedGroupingId(userId, "Work");
            long firstCategoryId = storedCategoryId(userId, firstGroupingId, "Supplies");
            storedCategoryId(userId, secondGroupingId, "Supplies");
            StoredGrouping firstGrouping = new StoredGrouping(firstGroupingId, "Home");

            Optional<StoredCategory> found = adapter.findByGroupingAndName(userId, firstGrouping, "Supplies");

            assertThat(found).isPresent();
            assertThat(found.get().id()).isEqualTo(firstCategoryId);
        }

        @Test
        @DisplayName(
                "when called for a stored user's grouping that holds no category of that name - then answers nothing")
        void whenGroupingHoldsNoCategoryOfThatName_thenAnswersNothing() {
            long userId = storedUserId("no-matching-category-user");
            long groupingId = storedGroupingId(userId, "Entertainment");
            storedCategoryId(userId, groupingId, "Streaming");
            StoredGrouping grouping = new StoredGrouping(groupingId, "Entertainment");

            Optional<StoredCategory> found = adapter.findByGroupingAndName(userId, grouping, "Nonexistent");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with the first of two users' id and the second user's grouping of the same name holding a category of the same name - then answers nothing")
        void whenCalledWithFirstUserIdAndSecondUsersGrouping_thenAnswersNothing() {
            long firstUserId = storedUserId("first-cross-user");
            long secondUserId = storedUserId("second-cross-user");
            storedGroupingId(firstUserId, "Shared Grouping");
            long secondGroupingId = storedGroupingId(secondUserId, "Shared Grouping");
            storedCategoryId(secondUserId, secondGroupingId, "Shared Category");
            StoredGrouping secondUsersGrouping = new StoredGrouping(secondGroupingId, "Shared Grouping");

            Optional<StoredCategory> found =
                    adapter.findByGroupingAndName(firstUserId, secondUsersGrouping, "Shared Category");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a stored user's id, a grouping and the name of that user's own parentless row - then answers nothing, since a grouping is not a category under another grouping")
        void whenNameNamesAParentlessRow_thenAnswersNothing() {
            long userId = storedUserId("parentless-row-user");
            storedGroupingId(userId, "Travel");
            long groupingId = storedGroupingId(userId, "Insurance");
            StoredGrouping grouping = new StoredGrouping(groupingId, "Insurance");

            Optional<StoredCategory> found = adapter.findByGroupingAndName(userId, grouping, "Travel");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("checking whether a user's category carries a name")
    class ExistsByUserIdAndName {

        @Test
        @DisplayName(
                "when called with a stored user's id and a category name stored under a grouping - then answers true")
        void whenNameNamesAStoredCategory_thenAnswersTrue() {
            long userId = storedUserId("existing-category-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            storedCategoryId(userId, groupingId, "Supermarkets");

            boolean exists = adapter.existsByUserIdAndName(userId, "Supermarkets");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName(
                "when called with a stored user's id and the name of only a parentless row of theirs - then answers false")
        void whenNameNamesOnlyAParentlessRow_thenAnswersFalse() {
            long userId = storedUserId("only-grouping-user");
            storedGroupingId(userId, "Groceries");

            boolean exists = adapter.existsByUserIdAndName(userId, "Groceries");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName(
                "when called with the first of two users' id and a category name owned by the second user - then answers false")
        void whenNameNamesTheSecondUsersCategory_thenAnswersFalse() {
            long firstUserId = storedUserId("first-exists-user");
            long secondUserId = storedUserId("second-exists-user");
            long secondGroupingId = storedGroupingId(secondUserId, "Groceries");
            storedCategoryId(secondUserId, secondGroupingId, "Supermarkets");

            boolean exists = adapter.existsByUserIdAndName(firstUserId, "Supermarkets");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("finding all of a user's categories")
    class FindAllForUser {

        @Test
        @DisplayName(
                "when called with no grouping id - then every category comes back, each naming its grouping's id and name")
        void whenCalledWithNoGroupingId_thenEveryCategoryComesBackNamingItsGroupingIdAndName() {
            long userId = storedUserId("find-all-categories-no-grouping-user");
            long homeGroupingId = storedGroupingId(userId, "Home");
            long workGroupingId = storedGroupingId(userId, "Work");
            long rentCategoryId = storedCategoryId(userId, homeGroupingId, "Rent");
            long suppliesCategoryId = storedCategoryId(userId, workGroupingId, "Supplies");

            List<CategoryEntry> categories = adapter.findAllForUser(userId, null);

            assertThat(categories)
                    .containsExactlyInAnyOrder(
                            new CategoryEntry(rentCategoryId, "Rent", homeGroupingId, "Home"),
                            new CategoryEntry(suppliesCategoryId, "Supplies", workGroupingId, "Work"));
        }

        @Test
        @DisplayName("when called with one grouping's id - then only that grouping's categories come back")
        void whenCalledWithOneGroupingsId_thenOnlyThatGroupingsCategoriesComeBack() {
            long userId = storedUserId("find-all-categories-one-grouping-user");
            long homeGroupingId = storedGroupingId(userId, "Home");
            long workGroupingId = storedGroupingId(userId, "Work");
            long rentCategoryId = storedCategoryId(userId, homeGroupingId, "Rent");
            storedCategoryId(userId, workGroupingId, "Supplies");

            List<CategoryEntry> categories = adapter.findAllForUser(userId, homeGroupingId);

            assertThat(categories).singleElement().satisfies(entry -> assertThat(entry.id())
                    .isEqualTo(rentCategoryId));
        }

        @Test
        @DisplayName(
                "when called with a grouping id belonging to another user, and separately one that names no grouping at all - then an empty list comes back, and nothing is thrown")
        void whenCalledWithAnotherUsersGroupingIdAndWithUnknownGroupingId_thenEmptyListComesBackAndNothingThrown() {
            long userId = storedUserId("find-all-categories-bad-grouping-ids-user");
            long groupingId = storedGroupingId(userId, "Home");
            storedCategoryId(userId, groupingId, "Rent");
            long otherUserId = storedUserId("find-all-categories-bad-grouping-ids-other-user");
            long otherUsersGroupingId = storedGroupingId(otherUserId, "Other");

            List<CategoryEntry> ownCategories = adapter.findAllForUser(userId, null);
            List<CategoryEntry> withOtherUsersGrouping = adapter.findAllForUser(userId, otherUsersGroupingId);
            List<CategoryEntry> withUnknownGrouping = adapter.findAllForUser(userId, 999_999_999L);

            assertThat(ownCategories).hasSize(1);
            assertThat(withOtherUsersGrouping).isEmpty();
            assertThat(withUnknownGrouping).isEmpty();
        }

        @Test
        @DisplayName(
                "when called for the first of two users, the second owning their own categories - then none of the other user's categories appears")
        void whenCalledForFirstOfTwoUsers_thenNoneOfSecondUsersCategoriesAppears() {
            long firstUserId = storedUserId("find-all-categories-first-user");
            long secondUserId = storedUserId("find-all-categories-second-user");
            long firstGroupingId = storedGroupingId(firstUserId, "First Grouping");
            long firstCategoryId = storedCategoryId(firstUserId, firstGroupingId, "First Category");
            long secondGroupingId = storedGroupingId(secondUserId, "Second Grouping");
            storedCategoryId(secondUserId, secondGroupingId, "Second Category");

            List<CategoryEntry> categories = adapter.findAllForUser(firstUserId, null);

            assertThat(categories).singleElement().satisfies(entry -> assertThat(entry.id())
                    .isEqualTo(firstCategoryId));
        }

        @Test
        @DisplayName(
                "when called for a user with a grouping and no categories under it - then the grouping row itself is not answered as a category")
        void whenGroupingHasNoCategories_thenGroupingRowItselfIsNotAnsweredAsACategory() {
            long userId = storedUserId("find-all-categories-empty-grouping-user");
            long populatedGroupingId = storedGroupingId(userId, "Populated");
            long populatedCategoryId = storedCategoryId(userId, populatedGroupingId, "Category");
            storedGroupingId(userId, "Empty Grouping");

            List<CategoryEntry> categories = adapter.findAllForUser(userId, null);

            assertThat(categories).singleElement().satisfies(entry -> assertThat(entry.id())
                    .isEqualTo(populatedCategoryId));
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
        void
                whenFindByGroupingAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.findByUserIdAndParentIdAndName(any(), any(), any()))
                    .thenThrow(frameworkException);
            StoredGrouping grouping = new StoredGrouping(1L, "Groceries");

            assertThatThrownBy(() -> mockedAdapter.findByGroupingAndName(1L, grouping, "Supermarkets"))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when existsByUserIdAndName() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenExistsByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.existsByUserIdAndNameAndParentIdIsNotNull(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.existsByUserIdAndName(1L, "Groceries"))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        // A default answer that throws for any call, rather than a stub on one method, so the
        // scenario stays about the failure surfacing and not about which query the adapter runs.
        @Test
        @DisplayName(
                "when findAllForUser() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindAllForUserHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            CategoryEntityRepository throwingRepository = mock(CategoryEntityRepository.class, invocation -> {
                throw frameworkException;
            });
            CategoryRepositoryAdapter throwingAdapter = new CategoryRepositoryAdapter(throwingRepository);

            assertThatThrownBy(() -> throwingAdapter.findAllForUser(1L, null))
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
