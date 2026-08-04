package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.dto.StoredCategory;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListCategoriesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 2L;

    private UserRepository userRepository;
    private CategoryRepository categoryRepository;
    private ListCategoriesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        Logger log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(ListCategoriesUseCase.class)).thenReturn(log);
        useCase = new ListCategoriesUseCase(userRepository, categoryRepository, loggerFactory);
    }

    private ListCategoriesCommand newListCategories(String groupingName) {
        return new ListCategoriesCommand(new AuthenticatedUserId(EXTERNAL_ID), groupingName);
    }

    @Nested
    @DisplayName("listing a grouping's categories")
    class List {

        @Test
        @DisplayName("when the command is absent - then throws InvalidCategoryException and neither repository is "
                + "touched")
        void whenCommandIsAbsent_thenThrowsInvalidCategoryExceptionAndRepositoriesAreUntouched() {
            assertThatThrownBy(() -> useCase.list(null)).isInstanceOf(InvalidCategoryException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws "
                + "EntityNotFoundException and the category repository is never called")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndCategoryRepositoryUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> useCase.list(newListCategories("Groceries")));

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when findByUserIdAndName answers an empty list for the name - then throws "
                + "InvalidCategoryException whose message names the grouping asked for and says none is stored "
                + "for this user")
        void whenFindByUserIdAndNameAnswersEmptyList_thenThrowsInvalidCategoryExceptionNamingGroupingAsUnstored() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(java.util.List.of());

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isInstanceOf(InvalidCategoryException.class)
                    .hasMessageContaining("Groceries")
                    .hasMessageContaining("stored");
        }

        @Test
        @DisplayName("when findByUserIdAndName answers only candidates carrying a parent name - then throws "
                + "InvalidCategoryException saying that name is a category, not a grouping, and findChildNames is "
                + "never called")
        void whenAllCandidatesCarryAParentName_thenThrowsInvalidCategoryExceptionSayingCategoryNotGrouping() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(categoryRepository.findByUserIdAndName(USER_ID, "Coffee"))
                    .thenReturn(java.util.List.of(new StoredCategory(CATEGORY_ID, "Coffee", Optional.of("Groceries"))));

            assertThatThrownBy(() -> useCase.list(newListCategories("Coffee")))
                    .isInstanceOf(InvalidCategoryException.class)
                    .hasMessageContaining("Coffee")
                    .hasMessageContaining("category, not a grouping");

            verify(categoryRepository, never()).findChildNames(anyLong());
        }

        @Test
        @DisplayName("when findByUserIdAndName answers two candidates for the name - one with a parent name and "
                + "one without, as Travel resolves in the default catalogue - then findChildNames is called with "
                + "the parentless candidate's id and its answer is returned")
        void whenCandidatesIncludeOneParentlessAndOneWithParent_thenFindChildNamesReceivesParentlessCandidatesId() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            long groupingId = CATEGORY_ID;
            long leafId = 3L;
            when(categoryRepository.findByUserIdAndName(USER_ID, "Travel"))
                    .thenReturn(java.util.List.of(
                            new StoredCategory(leafId, "Travel", Optional.of("Shopping")),
                            new StoredCategory(groupingId, "Travel", Optional.empty())));
            java.util.List<String> children = java.util.List.of("Flights", "Hotels");
            when(categoryRepository.findChildNames(groupingId)).thenReturn(children);

            java.util.List<String> result = useCase.list(newListCategories("Travel"));

            verify(categoryRepository).findChildNames(groupingId);
            assertThat(result).isEqualTo(children);
        }

        @Test
        @DisplayName("when the stored user's grouping has children - then returns those child names, ordered by "
                + "name, exactly as the repository answered them")
        void whenGroupingHasChildren_thenReturnsChildNamesExactlyAsRepositoryAnswered() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
                    .thenReturn(java.util.List.of(new StoredCategory(CATEGORY_ID, "Groceries", Optional.empty())));
            java.util.List<String> children = java.util.List.of("Coffee", "Restaurant");
            when(categoryRepository.findChildNames(CATEGORY_ID)).thenReturn(children);

            java.util.List<String> result = useCase.list(newListCategories("Groceries"));

            assertThat(result).isEqualTo(children);
        }

        @Test
        @DisplayName("when the stored user's id differs from the external id on the command - then "
                + "findByUserIdAndName receives that stored user's id and the command's name, and findChildNames "
                + "receives the id of the candidate that read answered")
        void whenStoredUsersIdDiffersFromExternalId_thenRepositoriesReceiveStoredUsersIdNotExternalId() {
            long differentUserId = 42L;
            User storedUser = User.stored(differentUserId, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(categoryRepository.findByUserIdAndName(differentUserId, "Groceries"))
                    .thenReturn(java.util.List.of(new StoredCategory(CATEGORY_ID, "Groceries", Optional.empty())));
            when(categoryRepository.findChildNames(CATEGORY_ID)).thenReturn(java.util.List.of("Coffee"));

            useCase.list(newListCategories("Groceries"));

            verify(categoryRepository).findByUserIdAndName(differentUserId, "Groceries");
            verify(categoryRepository).findChildNames(CATEGORY_ID);
        }

        @Test
        @DisplayName("when the stored user's grouping has no children - then returns an empty list rather than "
                + "throwing")
        void whenGroupingHasNoChildren_thenReturnsEmptyListRatherThanThrowing() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
                    .thenReturn(java.util.List.of(new StoredCategory(CATEGORY_ID, "Groceries", Optional.empty())));
            when(categoryRepository.findChildNames(CATEGORY_ID)).thenReturn(java.util.List.of());

            java.util.List<String> result = useCase.list(newListCategories("Groceries"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when findByUserIdAndName raises PersistenceFailedException - then the exception reaches the "
                + "caller unchanged and findChildNames is never called")
        void whenFindByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries")).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);

            verify(categoryRepository, never()).findChildNames(anyLong());
        }

        @Test
        @DisplayName("when findChildNames raises PersistenceFailedException - then the exception reaches the "
                + "caller unchanged")
        void whenFindChildNamesRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
                    .thenReturn(java.util.List.of(new StoredCategory(CATEGORY_ID, "Groceries", Optional.empty())));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.findChildNames(CATEGORY_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);
        }
    }
}
