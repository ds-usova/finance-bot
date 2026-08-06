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
                "when a user row is stored under the external id - then returns the user carrying its generated database id and its external id")
        void whenUserRowStoredUnderExternalId_thenReturnsUserCarryingGeneratedIdAndExternalId() {
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
        @DisplayName(
                "when called with an unstored user and Grouping.defaults() - then the user row is written and the returned user carries its generated database id")
        void whenCalledWithUnstoredUserAndDefaultCategories_thenUserRowWrittenAndReturnedUserCarriesGeneratedId() {
            User user = User.newUser("new-user-external-id");

            User createdUser = adapter.create(user, Grouping.defaults());

            assertThat(createdUser.id()).isPresent();
            assertThat(createdUser.externalId()).isEqualTo("new-user-external-id");
        }

        @Test
        @DisplayName(
                "when called with an unstored user and Grouping.defaults() - then 97 category rows exist for that user, 20 with no parent and each remaining row pointing at the row of the group it belongs to, matching the tree by name")
        void whenCalledWithUnstoredUserAndDefaultCategories_thenCategoryTreeIsWrittenMatchingByName() {
            User user = User.newUser("category-tree-external-id");

            User createdUser = adapter.create(user, Grouping.defaults());

            assertCategoryTreeWritten(createdUser.id().orElseThrow(), Grouping.defaults());
        }

        @Test
        @DisplayName(
                "when called with an unstored user carrying an already-stored external id - then returns that user and writes no categories")
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
                "when called with an external id exactly 255 characters long - then the user is written and returned with its generated id")
        void whenExternalIdIsExactly255Characters_thenUserIsWrittenAndReturnedWithGeneratedId() {
            String externalId = "a".repeat(255);
            User user = User.newUser(externalId);

            User createdUser = adapter.create(user, List.of());

            assertThat(createdUser.id()).isPresent();
            assertThat(createdUser.externalId()).isEqualTo(externalId);
        }

        @Test
        @DisplayName(
                "when called with an external id 256 characters long - then throws InvalidUserException before anything is written, so no user row exists afterwards")
        void whenExternalIdIs256Characters_thenThrowsInvalidUserExceptionBeforeWritingAnything() {
            String externalId = "a".repeat(256);
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of())).isInstanceOf(InvalidUserException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a catalogue whose grouping name is exactly 100 characters long - then the tree is written and that grouping's row carries the whole name")
        void whenGroupingNameIsExactly100Characters_thenTreeIsWrittenAndGroupingRowCarriesWholeName() {
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
                "when called with a category tree whose child name is exactly 100 characters long - then the tree is written and that child's row carries the whole name")
        void whenChildNameIsExactly100Characters_thenTreeIsWrittenAndChildRowCarriesWholeName() {
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
                "when called with a category tree whose child name is 101 characters long - then throws InvalidCategoryException before anything is written, so no user row exists afterwards")
        void whenChildNameIs101Characters_thenThrowsInvalidCategoryExceptionBeforeWritingAnything() {
            String childName = "a".repeat(101);
            Grouping tree = Grouping.of("boundary-group-2", childName);
            String externalId = "overlong-child-external-id";
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of(tree))).isInstanceOf(InvalidCategoryException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a category tree whose group name is 101 characters long - then throws InvalidGroupingException before anything is written, groupings are checked as well as categories")
        void whenGroupingNameIs101Characters_thenThrowsInvalidGroupingExceptionBeforeWritingAnything() {
            String groupName = "a".repeat(101);
            Grouping tree = Grouping.of(groupName, "Valid Child");
            String externalId = "overlong-group-external-id";
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of(tree))).isInstanceOf(InvalidGroupingException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called for two users one after the other - then each user owns its own 97 category rows and neither user's rows reference the other's")
        void whenCalledForTwoUsers_thenEachOwnsItsOwnCategoryRowsWithNoCrossReferences() {
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
                "when the database is unreachable - then findByExternalId() throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenDatabaseIsUnreachable_thenFindByExternalIdThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
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
        @DisplayName(
                "when create() hits a database failure that is not a constraint violation - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
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
                "when the store hands generated group ids back in an order that does not match the input - then each child still pairs to the right group by name")
        void whenGroupOrderIsNotPreserved_thenChildrenStillPairToTheRightGroupByName() {
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
