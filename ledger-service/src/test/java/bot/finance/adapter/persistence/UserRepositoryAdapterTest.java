package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import bot.finance.domain.value.Grouping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(UserRepositoryAdapter.class)
class UserRepositoryAdapterTest {

    @Autowired
    private UserRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("finding a user by its external id")
    class FindByExternalId {

        @Test
        @DisplayName(
                "when a user row is stored under the external id - then returns that user with its id and external id")
        void whenUserRowStoredUnderExternalId_thenReturnsThatUserWithItsIdAndExternalId() {
            UserEntity stored = userEntityRepository.save(new UserEntity(null, "existing-external-id"));

            Optional<User> found = adapter.findByExternalId("existing-external-id");

            assertThat(found).isPresent();
            assertThat(found.get().id()).contains(stored.id());
            assertThat(found.get().externalId()).isEqualTo("existing-external-id");
        }

        @Test
        @DisplayName("when nothing is stored under the external id - then returns an empty result")
        void whenNothingStoredUnderExternalId_thenReturnsEmptyResult() {
            Optional<User> found = adapter.findByExternalId("unknown-external-id");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("creating a user")
    class Create {

        @Test
        @DisplayName("when called with an unstored user - then the returned user carries its generated database id")
        void whenCalledWithUnstoredUser_thenReturnedUserCarriesGeneratedId() {
            User user = User.newUser("new-user-external-id");

            User createdUser = adapter.create(user, Grouping.defaults());

            assertThat(createdUser.id()).isPresent();
            assertThat(createdUser.externalId()).isEqualTo("new-user-external-id");
        }

        @Test
        @DisplayName(
                "when called with Grouping.defaults() - then the whole category tree is written, each child under its group")
        void whenCalledWithDefaultCategories_thenCategoryTreeIsWrittenMatchingByName() {
            User user = User.newUser("category-tree-external-id");

            User createdUser = adapter.create(user, Grouping.defaults());

            assertCategoryTreeWritten(createdUser.id().orElseThrow(), Grouping.defaults());
        }

        @Test
        @DisplayName("when the external id is already stored - then returns that user and writes no categories")
        void whenCalledWithAlreadyStoredExternalId_thenReturnsThatUserAndWritesNoCategories() {
            UserEntity stored = userEntityRepository.save(new UserEntity(null, "duplicate-external-id"));
            User duplicateUser = User.newUser("duplicate-external-id");

            User result = adapter.create(duplicateUser, Grouping.defaults());

            assertThat(result.id()).contains(stored.id());
            assertThat(result.externalId()).isEqualTo("duplicate-external-id");
            assertThat(categoryRowsFor(stored.id())).isEmpty();
        }

        @Test
        @DisplayName(
                "when the external id is exactly 255 characters long - then the user comes back with it and a generated id")
        void whenExternalIdIsExactly255Characters_thenUserComesBackWithItAndAGeneratedId() {
            String externalId = "a".repeat(255);
            User user = User.newUser(externalId);

            User createdUser = adapter.create(user, List.of());

            assertThat(createdUser.id()).isPresent();
            assertThat(createdUser.externalId()).isEqualTo(externalId);
        }

        @Test
        @DisplayName(
                "when the external id is 256 characters long - then throws InvalidUserException and writes nothing")
        void whenExternalIdIs256Characters_thenThrowsInvalidUserExceptionAndWritesNothing() {
            String externalId = "a".repeat(256);
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of())).isInstanceOf(InvalidUserException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when a grouping name is exactly 100 characters long - then that grouping's row carries the whole name")
        void whenGroupingNameIsExactly100Characters_thenGroupingRowCarriesWholeName() {
            String groupingName = "a".repeat(100);
            Grouping tree = Grouping.of(groupingName, "Boundary Child");
            User user = User.newUser("boundary-grouping-external-id");

            User createdUser = adapter.create(user, List.of(tree));

            List<CategoryEntity> rows = categoryRowsFor(createdUser.id().orElseThrow());
            assertThat(rows)
                    .filteredOn(row -> row.name().equals(groupingName))
                    .singleElement()
                    .satisfies(row -> assertThat(row.name()).hasSize(100));
        }

        @Test
        @DisplayName(
                "when a category name is exactly 100 characters long - then that category's row carries the whole name")
        void whenChildNameIsExactly100Characters_thenChildRowCarriesWholeName() {
            String childName = "a".repeat(100);
            Grouping tree = Grouping.of("boundary-group", childName);
            User user = User.newUser("boundary-child-external-id");

            User createdUser = adapter.create(user, List.of(tree));

            List<CategoryEntity> rows = categoryRowsFor(createdUser.id().orElseThrow());
            assertThat(rows)
                    .filteredOn(row -> row.name().equals(childName))
                    .singleElement()
                    .satisfies(row -> assertThat(row.name()).hasSize(100));
        }

        @Test
        @DisplayName(
                "when a category name is 101 characters long - then throws InvalidCategoryException and writes nothing")
        void whenChildNameIs101Characters_thenThrowsInvalidCategoryExceptionAndWritesNothing() {
            String childName = "a".repeat(101);
            Grouping tree = Grouping.of("boundary-group-2", childName);
            String externalId = "overlong-child-external-id";
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of(tree))).isInstanceOf(InvalidCategoryException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when a grouping name is 101 characters long - then throws InvalidGroupingException and writes nothing")
        void whenGroupingNameIs101Characters_thenThrowsInvalidGroupingExceptionAndWritesNothing() {
            String groupName = "a".repeat(101);
            Grouping tree = Grouping.of(groupName, "Valid Child");
            String externalId = "overlong-group-external-id";
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of(tree))).isInstanceOf(InvalidGroupingException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called for two users one after the other - then each owns its own category rows, sharing none")
        void whenCalledForTwoUsers_thenEachOwnsItsOwnCategoryRowsSharingNone() {
            User firstCreatedUser = adapter.create(User.newUser("first-user-external-id"), Grouping.defaults());
            User secondCreatedUser = adapter.create(User.newUser("second-user-external-id"), Grouping.defaults());

            long firstUserId = firstCreatedUser.id().orElseThrow();
            long secondUserId = secondCreatedUser.id().orElseThrow();

            assertCategoryTreeWritten(firstUserId, Grouping.defaults());
            assertCategoryTreeWritten(secondUserId, Grouping.defaults());

            List<Long> firstUserRowIds = categoryRowsFor(firstUserId).stream()
                    .map(CategoryEntity::id)
                    .toList();
            List<Long> secondUserRowIds = categoryRowsFor(secondUserId).stream()
                    .map(CategoryEntity::id)
                    .toList();
            assertThat(firstUserRowIds).doesNotContainAnyElementsOf(secondUserRowIds);
        }
    }

    // The scenarios below need a store that misbehaves in ways the healthy containerized
    // Postgres cannot be made to: an unreachable connection, a non-constraint failure, and a
    // batch insert that hands generated ids back out of input order. Each constructs its own
    // adapter over Mockito mocks and calls the adapter's own public methods directly - it is
    // still the adapter under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final UserEntityRepository mockedUserEntityRepository = mock(UserEntityRepository.class);
        private final JdbcAggregateTemplate mockedJdbcAggregateTemplate = mock(JdbcAggregateTemplate.class);
        private final UserRepositoryAdapter mockedAdapter =
                new UserRepositoryAdapter(mockedUserEntityRepository, mockedJdbcAggregateTemplate);

        @Test
        @DisplayName(
                "when the database is unreachable - then findByExternalId() throws PersistenceFailedException wrapping it")
        void whenDatabaseIsUnreachable_thenFindByExternalIdThrowsPersistenceFailedExceptionWrappingIt() {
            DataAccessResourceFailureException frameworkException =
                    new DataAccessResourceFailureException("connection refused");
            when(mockedUserEntityRepository.findByExternalId("unreachable-external-id"))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findByExternalId("unreachable-external-id"))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName("when create() hits a non-constraint failure - then throws PersistenceFailedException wrapping it")
        void whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedUserEntityRepository.insertIfAbsent(any())).thenThrow(frameworkException);
            User user = User.newUser("non-constraint-failure-external-id");

            assertThatThrownBy(() -> mockedAdapter.create(user, List.of()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when the store hands generated group ids back out of input order - then each child still pairs by name")
        void whenGroupOrderIsNotPreserved_thenChildrenStillPairByName() {
            when(mockedUserEntityRepository.insertIfAbsent(any())).thenReturn(Optional.of(1L));

            Grouping first = Grouping.of("First", "First Child");
            Grouping second = Grouping.of("Second", "Second Child");

            AtomicReference<List<CategoryEntity>> capturedChildren = new AtomicReference<>();
            // Reverses the group order it was given before assigning generated ids - the store's
            // insertAll is not guaranteed to preserve input order, unlike the real containerized
            // Postgres this class otherwise runs against.
            when(mockedJdbcAggregateTemplate.<CategoryEntity>insertAll(any()))
                    .thenAnswer(invocation -> reversedWithGeneratedIds(invocation.getArgument(0)))
                    .thenAnswer(invocation -> {
                        List<CategoryEntity> children = invocation.getArgument(0);
                        capturedChildren.set(children);
                        return children;
                    });

            mockedAdapter.create(User.newUser("reordered-groups-external-id"), List.of(first, second));

            Map<String, Long> parentIdByChildName = capturedChildren.get().stream()
                    .collect(Collectors.toMap(CategoryEntity::name, CategoryEntity::parentId));
            assertThat(parentIdByChildName)
                    .as("children paired to their group by name, not by insertAll's return position")
                    .containsEntry("First Child", 101L)
                    .containsEntry("Second Child", 100L);
        }

        private List<CategoryEntity> reversedWithGeneratedIds(List<CategoryEntity> givenGroups) {
            List<CategoryEntity> reversed = new ArrayList<>(givenGroups);
            Collections.reverse(reversed);
            List<CategoryEntity> withGeneratedIds = new ArrayList<>();
            long id = 100;
            for (CategoryEntity group : reversed) {
                withGeneratedIds.add(new CategoryEntity(id++, group.userId(), group.parentId(), group.name()));
            }
            return withGeneratedIds;
        }
    }

    private List<CategoryEntity> categoryRowsFor(long userId) {
        return CategoryRowUtils.categoryRowsFor(jdbcAggregateTemplate, userId);
    }

    private void assertCategoryTreeWritten(long userId, List<Grouping> expectedTree) {
        List<CategoryEntity> rows = categoryRowsFor(userId);
        assertThat(rows).hasSize(97);

        List<CategoryEntity> groupRows =
                rows.stream().filter(row -> row.parentId() == null).toList();
        List<CategoryEntity> childRows =
                rows.stream().filter(row -> row.parentId() != null).toList();
        assertThat(groupRows).hasSize(20);
        assertThat(childRows).hasSize(77);

        Map<String, Long> groupIdByName =
                groupRows.stream().collect(Collectors.toMap(CategoryEntity::name, CategoryEntity::id));

        for (Grouping grouping : expectedTree) {
            Long groupId = groupIdByName.get(grouping.name());
            assertThat(groupId).as("group row for '%s'", grouping.name()).isNotNull();

            for (Category category : grouping.categories()) {
                assertThat(childRows)
                        .as("child row for '%s' under '%s'", category.name(), grouping.name())
                        .anyMatch(row -> row.name().equals(category.name()) && groupId.equals(row.parentId()));
            }
        }
    }
}
