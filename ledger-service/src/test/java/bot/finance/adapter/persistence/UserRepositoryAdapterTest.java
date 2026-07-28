package bot.finance.adapter.persistence;

import bot.finance.common.PersistenceAdapterTest;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        @DisplayName("when a user row is stored under the external id - then returns the user carrying its generated database id and its external id")
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
        @DisplayName("when called with an unstored user and Category.defaults() - then the user row is written and the returned user carries its generated database id")
        void whenCalledWithUnstoredUserAndDefaultCategories_thenUserRowWrittenAndReturnedUserCarriesGeneratedId() {
            User user = User.newUser("new-user-external-id");

            User createdUser = adapter.create(user, Category.defaults());

            assertThat(createdUser.id()).isPresent();
            assertThat(createdUser.externalId()).isEqualTo("new-user-external-id");
        }

        @Test
        @DisplayName("when called with an unstored user and Category.defaults() - then 98 category rows exist for that user, 20 with no parent and each remaining row pointing at the row of the group it belongs to, matching the tree by name")
        void whenCalledWithUnstoredUserAndDefaultCategories_thenCategoryTreeIsWrittenMatchingByName() {
            User user = User.newUser("category-tree-external-id");

            User createdUser = adapter.create(user, Category.defaults());

            assertCategoryTreeWritten(createdUser.id().orElseThrow(), Category.defaults());
        }

        @Test
        @DisplayName("when called with an unstored user carrying an external id already stored - then the unique constraint rejects the insert and a PersistenceFailedException is raised, carrying the framework exception it replaced as its cause")
        void whenCalledWithAlreadyStoredExternalId_thenPersistenceFailedExceptionCarriesFrameworkExceptionAsCause() {
            userEntityRepository.save(new UserEntity(null, "duplicate-external-id"));
            User duplicateUser = User.newUser("duplicate-external-id");

            assertThatThrownBy(() -> adapter.create(duplicateUser, List.of()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("when called with an external id exactly 255 characters long - then the user is written and returned with its generated id")
        void whenExternalIdIsExactly255Characters_thenUserIsWrittenAndReturnedWithGeneratedId() {
            String externalId = "a".repeat(255);
            User user = User.newUser(externalId);

            User createdUser = adapter.create(user, List.of());

            assertThat(createdUser.id()).isPresent();
            assertThat(createdUser.externalId()).isEqualTo(externalId);
        }

        @Test
        @DisplayName("when called with an external id 256 characters long - then throws InvalidUserException before anything is written, so no user row exists afterwards")
        void whenExternalIdIs256Characters_thenThrowsInvalidUserExceptionBeforeWritingAnything() {
            String externalId = "a".repeat(256);
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of()))
                    .isInstanceOf(InvalidUserException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName("when called with a category tree whose child name is exactly 100 characters long - then the tree is written and that child's row carries the whole name")
        void whenChildNameIsExactly100Characters_thenTreeIsWrittenAndChildRowCarriesWholeName() {
            String childName = "a".repeat(100);
            Category tree = Category.group("boundary-group", childName);
            User user = User.newUser("boundary-child-external-id");

            User createdUser = adapter.create(user, List.of(tree));

            List<CategoryEntity> rows = categoryRowsFor(createdUser.id().orElseThrow());
            assertThat(rows)
                    .filteredOn(row -> row.name().equals(childName))
                    .singleElement()
                    .satisfies(row -> assertThat(row.name()).hasSize(100));
        }

        @Test
        @DisplayName("when called with a category tree whose child name is 101 characters long - then throws InvalidCategoryException before anything is written, so no user row exists afterwards")
        void whenChildNameIs101Characters_thenThrowsInvalidCategoryExceptionBeforeWritingAnything() {
            String childName = "a".repeat(101);
            Category tree = Category.group("boundary-group-2", childName);
            String externalId = "overlong-child-external-id";
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of(tree)))
                    .isInstanceOf(InvalidCategoryException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName("when called with a category tree whose group name is 101 characters long - then throws InvalidCategoryException before anything is written, groups are checked as well as children")
        void whenGroupNameIs101Characters_thenThrowsInvalidCategoryExceptionBeforeWritingAnything() {
            String groupName = "a".repeat(101);
            Category tree = Category.group(groupName, "Valid Child");
            String externalId = "overlong-group-external-id";
            User user = User.newUser(externalId);

            assertThatThrownBy(() -> adapter.create(user, List.of(tree)))
                    .isInstanceOf(InvalidCategoryException.class);

            assertThat(userEntityRepository.findByExternalId(externalId)).isEmpty();
        }

        @Test
        @DisplayName("when called for two users one after the other - then each user owns its own 98 category rows and neither user's rows reference the other's")
        void whenCalledForTwoUsers_thenEachOwnsItsOwnCategoryRowsWithNoCrossReferences() {
            User firstCreatedUser = adapter.create(User.newUser("first-user-external-id"), Category.defaults());
            User secondCreatedUser = adapter.create(User.newUser("second-user-external-id"), Category.defaults());

            long firstUserId = firstCreatedUser.id().orElseThrow();
            long secondUserId = secondCreatedUser.id().orElseThrow();

            assertCategoryTreeWritten(firstUserId, Category.defaults());
            assertCategoryTreeWritten(secondUserId, Category.defaults());

            List<Long> firstUserRowIds = categoryRowsFor(firstUserId).stream().map(CategoryEntity::id).toList();
            List<Long> secondUserRowIds = categoryRowsFor(secondUserId).stream().map(CategoryEntity::id).toList();
            assertThat(firstUserRowIds).doesNotContainAnyElementsOf(secondUserRowIds);
        }

    }

    private List<CategoryEntity> categoryRowsFor(long userId) {
        return jdbcAggregateTemplate.findAll(CategoryEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    private void assertCategoryTreeWritten(long userId, List<Category> expectedTree) {
        List<CategoryEntity> rows = categoryRowsFor(userId);
        assertThat(rows).hasSize(98);

        List<CategoryEntity> groupRows = rows.stream().filter(row -> row.parentId() == null).toList();
        List<CategoryEntity> childRows = rows.stream().filter(row -> row.parentId() != null).toList();
        assertThat(groupRows).hasSize(20);
        assertThat(childRows).hasSize(78);

        Map<String, Long> groupIdByName = groupRows.stream()
                .collect(Collectors.toMap(CategoryEntity::name, CategoryEntity::id));

        for (Category group : expectedTree) {
            Long groupId = groupIdByName.get(group.name());
            assertThat(groupId).as("group row for '%s'", group.name()).isNotNull();

            for (Category child : group.children()) {
                assertThat(childRows)
                        .as("child row for '%s' under '%s'", child.name(), group.name())
                        .anyMatch(row -> row.name().equals(child.name()) && groupId.equals(row.parentId()));
            }
        }
    }

}
