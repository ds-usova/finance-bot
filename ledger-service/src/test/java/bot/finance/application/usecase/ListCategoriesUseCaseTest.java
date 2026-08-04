package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListCategoriesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long GROUPING_ID = 2L;

    private UserRepository userRepository;
    private GroupingRepository groupingRepository;
    private CategoryRepository categoryRepository;
    private ListCategoriesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        groupingRepository = mock(GroupingRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        useCase = new ListCategoriesUseCase(userRepository, groupingRepository, categoryRepository);
    }

    private ListCategoriesCommand newListCategories(String groupingName) {
        return new ListCategoriesCommand(new AuthenticatedUserId(EXTERNAL_ID), groupingName);
    }

    @Nested
    @DisplayName("listing a grouping's categories")
    class Listing {

        @Test
        @DisplayName("when the command is absent - then throws InvalidGroupingException and neither repository is "
                + "touched")
        void whenCommandIsAbsent_thenThrowsInvalidGroupingExceptionAndRepositoriesAreUntouched() {
            assertThatThrownBy(() -> useCase.list(null)).isInstanceOf(InvalidGroupingException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws "
                + "EntityNotFoundException and the grouping and category repositories are never called")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndCategoryRepositoryUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when findByUserIdAndName answers a grouping - then findCategoryNames is called with that "
                + "user's stored id and that grouping, its answer is returned unchanged, and categoryRepository is "
                + "never touched")
        void whenGroupingHasChildren_thenReturnsChildNamesExactlyAsRepositoryAnswered() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            StoredGrouping grouping = new StoredGrouping(GROUPING_ID, "Groceries");
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.of(grouping));
            List<String> children = List.of("Coffee", "Restaurant");
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(children);

            List<String> result = useCase.list(newListCategories("Groceries"));

            assertThat(result).isEqualTo(children);
            verify(groupingRepository).findCategoryNames(USER_ID, grouping);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when findByUserIdAndName answers nothing and existsByUserIdAndName answers true - then throws "
                + "InvalidGroupingException whose message names the name asked for and says it is a category, not "
                + "a grouping, existsByUserIdAndName received the stored user's id, and findCategoryNames is never "
                + "called")
        void
                whenFindByUserIdAndNameAnswersEmptyAndCategoryExists_thenThrowsInvalidGroupingExceptionSayingCategoryNotGrouping() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(groupingRepository.findByUserIdAndName(USER_ID, "Coffee")).thenReturn(Optional.empty());
            when(categoryRepository.existsByUserIdAndName(USER_ID, "Coffee")).thenReturn(true);

            assertThatThrownBy(() -> useCase.list(newListCategories("Coffee")))
                    .isInstanceOf(InvalidGroupingException.class)
                    .hasMessageContaining("Coffee")
                    .hasMessageContaining("category, not a grouping");

            verify(categoryRepository).existsByUserIdAndName(USER_ID, "Coffee");
            verify(groupingRepository, never()).findCategoryNames(anyLong(), any());
        }

        @Test
        @DisplayName("when findByUserIdAndName answers nothing and existsByUserIdAndName answers false - then "
                + "throws InvalidGroupingException whose message names the name asked for and says no grouping of "
                + "that name is stored for this user, and existsByUserIdAndName received the stored user's id")
        void
                whenFindByUserIdAndNameAnswersEmptyAndCategoryDoesNotExist_thenThrowsInvalidGroupingExceptionNamingGroupingAsUnstored() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.empty());
            when(categoryRepository.existsByUserIdAndName(USER_ID, "Groceries")).thenReturn(false);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isInstanceOf(InvalidGroupingException.class)
                    .hasMessageContaining("Groceries")
                    .hasMessageContaining("stored");

            verify(categoryRepository).existsByUserIdAndName(USER_ID, "Groceries");
        }

        @Test
        @DisplayName("when findByUserIdAndName answers nothing and existsByUserIdAndName raises "
                + "PersistenceFailedException - then the exception reaches the caller unchanged")
        void whenExistsByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.empty());
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.existsByUserIdAndName(USER_ID, "Groceries")).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);
        }

        @Test
        @DisplayName("when a stored user's database id differs from the command's external id, and a grouping is "
                + "answered for it - then both reads receive that stored user's id, not the external id")
        void whenStoredUsersIdDiffersFromCommandsExternalId_thenBothReadsReceiveStoredUsersIdNotExternalId() {
            long differentUserId = 42L;
            User storedUser = User.stored(differentUserId, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            StoredGrouping grouping = new StoredGrouping(GROUPING_ID, "Groceries");
            when(groupingRepository.findByUserIdAndName(differentUserId, "Groceries"))
                    .thenReturn(Optional.of(grouping));
            when(groupingRepository.findCategoryNames(differentUserId, grouping))
                    .thenReturn(List.of("Coffee"));

            useCase.list(newListCategories("Groceries"));

            verify(groupingRepository).findByUserIdAndName(differentUserId, "Groceries");
            verify(groupingRepository).findCategoryNames(differentUserId, grouping);
        }

        @Test
        @DisplayName("when the stored user's grouping has no children - then returns an empty list rather than "
                + "throwing")
        void whenGroupingHasNoChildren_thenReturnsEmptyListRatherThanThrowing() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            StoredGrouping grouping = new StoredGrouping(GROUPING_ID, "Groceries");
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.of(grouping));
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(List.of());

            List<String> result = useCase.list(newListCategories("Groceries"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when findByUserIdAndName raises PersistenceFailedException - then the exception reaches the "
                + "caller unchanged and findCategoryNames is never called")
        void whenFindByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);

            verify(groupingRepository, never()).findCategoryNames(anyLong(), any());
        }

        @Test
        @DisplayName("when findCategoryNames raises PersistenceFailedException - then the exception reaches the "
                + "caller unchanged")
        void whenFindChildNamesRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            StoredGrouping grouping = new StoredGrouping(GROUPING_ID, "Groceries");
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.of(grouping));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);
        }
    }
}
