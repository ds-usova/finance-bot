package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.value.AuthenticatedUserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListCategoriesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 2L;

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
        @Disabled("RU07: assert the grouping repository is untouched as well")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndCategoryRepositoryUntouched() {
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());
            //
            // assertThatExceptionOfType(EntityNotFoundException.class)
            //         .isThrownBy(() -> useCase.list(newListCategories("Groceries")));
            //
            // verifyNoInteractions(groupingRepository);
            // verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when findByUserIdAndName answers nothing for the name - then throws "
                + "InvalidGroupingException whose message names the grouping asked for and says none is stored "
                + "for this user")
        @Disabled("RU07: replaced by the two refusal scenarios in the design")
        void whenFindByUserIdAndNameAnswersEmptyList_thenThrowsInvalidCategoryExceptionNamingGroupingAsUnstored() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.empty());
            //
            // assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
            //         .isInstanceOf(InvalidGroupingException.class)
            //         .hasMessageContaining("Groceries")
            //         .hasMessageContaining("stored");
        }

        @Test
        @DisplayName("when findByUserIdAndName answers nothing and existsByUserIdAndName answers true - then throws "
                + "InvalidGroupingException saying that name is a category, not a grouping, and findCategoryNames is "
                + "never called")
        @Disabled("RU07: the candidate filter is gone")
        void whenAllCandidatesCarryAParentName_thenThrowsInvalidCategoryExceptionSayingCategoryNotGrouping() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // when(groupingRepository.findByUserIdAndName(USER_ID, "Coffee")).thenReturn(Optional.empty());
            // when(categoryRepository.existsByUserIdAndName(USER_ID, "Coffee")).thenReturn(true);
            //
            // assertThatThrownBy(() -> useCase.list(newListCategories("Coffee")))
            //         .isInstanceOf(InvalidGroupingException.class)
            //         .hasMessageContaining("Coffee")
            //         .hasMessageContaining("category, not a grouping");
            //
            // verify(groupingRepository, never()).findCategoryNames(anyLong(), any());
        }

        @Test
        @DisplayName("when findByUserIdAndName answers two candidates for the name - one with a parent name and "
                + "one without, as Travel resolves in the default catalogue - then findCategoryNames is called with "
                + "the parentless candidate's id and its answer is returned")
        @Disabled("RU07: Travel resolving to the parentless row is now the query's parent_id IS NULL, covered by RI01")
        void whenCandidatesIncludeOneParentlessAndOneWithParent_thenFindChildNamesReceivesParentlessCandidatesId() {
            // no longer representable: findByUserIdAndName now answers at most one StoredGrouping
        }

        @Test
        @DisplayName("when the stored user's grouping has children - then returns those child names, ordered by "
                + "name, exactly as the repository answered them")
        @Disabled("RU07: read through groupingRepository.findCategoryNames")
        void whenGroupingHasChildren_thenReturnsChildNamesExactlyAsRepositoryAnswered() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // StoredGrouping grouping = new StoredGrouping(CATEGORY_ID, "Groceries");
            // when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.of(grouping));
            // List<String> children = List.of("Coffee", "Restaurant");
            // when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(children);
            //
            // List<String> result = useCase.list(newListCategories("Groceries"));
            //
            // assertThat(result).isEqualTo(children);
        }

        @Test
        @DisplayName("when a stored user's database id differs from the command's external id, and a grouping is "
                + "answered for it - then both reads receive that stored user's id, not the external id")
        @Disabled("RU07: replaced by the scoping scenario in the design")
        void whenStoredUsersIdDiffersFromExternalId_thenRepositoriesReceiveStoredUsersIdNotExternalId() {
            // long differentUserId = 42L;
            // User storedUser = User.stored(differentUserId, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // StoredGrouping grouping = new StoredGrouping(CATEGORY_ID, "Groceries");
            // when(groupingRepository.findByUserIdAndName(differentUserId, "Groceries"))
            //         .thenReturn(Optional.of(grouping));
            // when(groupingRepository.findCategoryNames(differentUserId, grouping)).thenReturn(List.of("Coffee"));
            //
            // useCase.list(newListCategories("Groceries"));
            //
            // verify(groupingRepository).findByUserIdAndName(differentUserId, "Groceries");
            // verify(groupingRepository).findCategoryNames(differentUserId, grouping);
        }

        @Test
        @DisplayName("when the stored user's grouping has no children - then returns an empty list rather than "
                + "throwing")
        @Disabled("RU07: stub findCategoryNames answering an empty list")
        void whenGroupingHasNoChildren_thenReturnsEmptyListRatherThanThrowing() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // StoredGrouping grouping = new StoredGrouping(CATEGORY_ID, "Groceries");
            // when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.of(grouping));
            // when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(List.of());
            //
            // List<String> result = useCase.list(newListCategories("Groceries"));
            //
            // assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when findByUserIdAndName raises PersistenceFailedException - then the exception reaches the "
                + "caller unchanged and findCategoryNames is never called")
        @Disabled("RU07: raise it from groupingRepository.findByUserIdAndName")
        void whenFindByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // PersistenceFailedException failure =
            //         new PersistenceFailedException("lookup failed", new RuntimeException());
            // when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenThrow(failure);
            //
            // assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
            //         .isSameAs(failure);
            //
            // verify(groupingRepository, never()).findCategoryNames(anyLong(), any());
        }

        @Test
        @DisplayName("when findCategoryNames raises PersistenceFailedException - then the exception reaches the "
                + "caller unchanged")
        @Disabled("RU07: raise it from groupingRepository.findCategoryNames")
        void whenFindChildNamesRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // StoredGrouping grouping = new StoredGrouping(CATEGORY_ID, "Groceries");
            // when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.of(grouping));
            // PersistenceFailedException failure =
            //         new PersistenceFailedException("lookup failed", new RuntimeException());
            // when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenThrow(failure);
            //
            // assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
            //         .isSameAs(failure);
        }
    }
}
