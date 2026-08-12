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
                "when a category of that name is stored under the grouping - then answers a StoredCategory with its id and name")
        void whenCalledForAStoredCategoryUnderThatGrouping_thenAnswersStoredCategoryWithIdAndName() {
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
                "when two groupings hold a category of the same name - then answers the one filed under the grouping given")
        void whenTwoGroupingsShareACategoryName_thenAnswersTheOneFiledUnderTheGroupingGiven() {
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
        @DisplayName("when the grouping given belongs to another user - then answers nothing")
        void whenGroupingGivenBelongsToAnotherUser_thenAnswersNothing() {
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
                "when the name given is that user's own parentless row - then answers nothing, a grouping is not a category")
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
        @DisplayName("when the grouping id given belongs to another user - then an empty list comes back")
        void whenCalledWithAnotherUsersGroupingId_thenEmptyListComesBack() {
            long userId = storedUserWithOneCategory("find-all-categories-other-users-grouping-user");
            long otherUserId = storedUserId("find-all-categories-other-users-grouping-other-user");
            long otherUsersGroupingId = storedGroupingId(otherUserId, "Other");

            List<CategoryEntry> categories = adapter.findAllForUser(userId, otherUsersGroupingId);

            assertThat(categories).isEmpty();
        }

        @Test
        @DisplayName("when the grouping id given names no grouping at all - then an empty list comes back")
        void whenCalledWithUnknownGroupingId_thenEmptyListComesBack() {
            long userId = storedUserWithOneCategory("find-all-categories-unknown-grouping-user");

            List<CategoryEntry> categories = adapter.findAllForUser(userId, 999_999_999L);

            assertThat(categories).isEmpty();
        }

        @Test
        @DisplayName("when another user owns categories too - then none of theirs appears")
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
        @DisplayName("when a grouping holds no categories - then the grouping row itself is not answered as a category")
        void whenGroupingHasNoCategories_thenGroupingRowItselfIsNotAnsweredAsACategory() {
            long userId = storedUserId("find-all-categories-empty-grouping-user");
            long populatedGroupingId = storedGroupingId(userId, "Populated");
            long populatedCategoryId = storedCategoryId(userId, populatedGroupingId, "Category");
            storedGroupingId(userId, "Empty Grouping");

            List<CategoryEntry> categories = adapter.findAllForUser(userId, null);

            assertThat(categories).singleElement().satisfies(entry -> assertThat(entry.id())
                    .isEqualTo(populatedCategoryId));
        }

        /**
         * A user owning exactly one category, read back through the adapter so that an empty answer to a
         * narrowed call is the grouping id being rejected and not the user having nothing to find.
         */
        private long storedUserWithOneCategory(String externalId) {
            long userId = storedUserId(externalId);
            storedCategoryId(userId, storedGroupingId(userId, "Home"), "Rent");
            assertThat(adapter.findAllForUser(userId, null)).hasSize(1);
            return userId;
        }
    }

    @Nested
    @DisplayName("checking whether a category is owned by the caller")
    class ExistsOwnedCategory {

        @Test
        @DisplayName(
                "when the id names a category of the caller's, filed under one of their groupings - then answers true")
        void whenIdNamesCallersCategoryUnderGrouping_thenAnswersTrue() {
            long userId = storedUserId("owned-category-user");
            long groupingId = storedGroupingId(userId, "Groceries");
            long categoryId = storedCategoryId(userId, groupingId, "Supermarkets");

            boolean exists = adapter.existsOwnedCategory(userId, categoryId);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("when the id names one of the caller's own groupings, which has no parent - then answers false")
        void whenIdNamesCallersOwnGrouping_thenAnswersFalse() {
            long userId = storedUserId("owned-grouping-user");
            long groupingId = storedGroupingId(userId, "Groceries");

            boolean exists = adapter.existsOwnedCategory(userId, groupingId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("when the id names another person's category - then answers false")
        void whenIdNamesAnotherPersonsCategory_thenAnswersFalse() {
            long ownerUserId = storedUserId("owned-category-owner-user");
            long groupingId = storedGroupingId(ownerUserId, "Groceries");
            long categoryId = storedCategoryId(ownerUserId, groupingId, "Supermarkets");
            long callerUserId = storedUserId("owned-category-caller-user");

            boolean exists = adapter.existsOwnedCategory(callerUserId, categoryId);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("when the id names no category row at all - then answers false")
        void whenIdNamesNoCategoryRow_thenAnswersFalse() {
            long userId = storedUserId("owned-category-unknown-id-user");

            boolean exists = adapter.existsOwnedCategory(userId, 999_999_999L);

            assertThat(exists).isFalse();
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
                "when findByGroupingAndName() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindByGroupingAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
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
                "when existsByUserIdAndName() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenExistsByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
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
                "when findAllForUser() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindAllForUserHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
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

        @Test
        @DisplayName(
                "when existsOwnedCategory() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenExistsOwnedCategoryHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedCategoryEntityRepository.existsByIdAndUserIdAndParentIdIsNotNull(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.existsOwnedCategory(1L, 1L))
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
