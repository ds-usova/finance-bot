package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.BrowseCategoriesCommand;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseCategoriesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long GROUPING_ID = 2L;

    private UserRepository userRepository;
    private CategoryRepository categoryRepository;
    private BrowseCategoriesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        useCase = new BrowseCategoriesUseCase(userRepository, categoryRepository);
    }

    private BrowseCategoriesCommand newCommand(Long groupingId) {
        return new BrowseCategoriesCommand(new AuthenticatedUserId(EXTERNAL_ID), groupingId);
    }

    @Nested
    @DisplayName("browsing categories")
    class Browse {

        @Test
        @DisplayName("when a stored user and a repository answering three categories - then all three are "
                + "answered, each carrying its grouping's id and name")
        void whenRepositoryAnswersThreeCategories_thenAllThreeAreAnsweredWithGroupingIdAndName() {
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            List<CategoryEntry> categories = List.of(
                    new CategoryEntry(1L, "Coffee", GROUPING_ID, "Groceries"),
                    new CategoryEntry(2L, "Restaurant", GROUPING_ID, "Groceries"),
                    new CategoryEntry(3L, "Fuel", 3L, "Transport"));
            when(categoryRepository.findAllForUser(USER_ID, null)).thenReturn(categories);

            List<CategoryEntry> result = useCase.browse(newCommand(null));

            assertThat(result).hasSize(3).isEqualTo(categories);
            assertThat(result)
                    .extracting(CategoryEntry::groupingId, CategoryEntry::groupingName)
                    .containsExactly(
                            tuple(GROUPING_ID, "Groceries"), tuple(GROUPING_ID, "Groceries"), tuple(3L, "Transport"));
        }

        @Test
        @DisplayName("when a stored user's database id differs from the caller's external id and a grouping id "
                + "is given - then the repository is asked with that stored id and the grouping id, never the "
                + "external id")
        void whenStoredUserIdDiffersFromExternalId_thenRepositoryReceivesStoredUserIdAndGroupingId() {
            long differentUserId = 42L;
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(differentUserId, EXTERNAL_ID)));
            when(categoryRepository.findAllForUser(differentUserId, GROUPING_ID)).thenReturn(List.of());

            useCase.browse(newCommand(GROUPING_ID));

            verify(categoryRepository).findAllForUser(differentUserId, GROUPING_ID);
        }

        @Test
        @DisplayName("when the given grouping id names no grouping of the stored user's - then an empty list is "
                + "answered and nothing is thrown")
        void whenGroupingIdNamesNoGroupingOfCaller_thenEmptyListIsAnsweredAndNothingThrown() {
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            long unknownGroupingId = 999L;
            when(categoryRepository.findAllForUser(USER_ID, unknownGroupingId)).thenReturn(List.of());

            List<CategoryEntry> result = useCase.browse(newCommand(unknownGroupingId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when no user row is stored under the caller's external id - then throws "
                + "EntityNotFoundException")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundException() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.browse(newCommand(null)))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(categoryRepository);
        }
    }
}
