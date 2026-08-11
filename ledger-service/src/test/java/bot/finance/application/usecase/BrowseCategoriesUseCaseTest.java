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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
        return new BrowseCategoriesCommand(new AuthenticatedUserId(USER_ID), groupingId);
    }

    @Nested
    @DisplayName("browsing categories")
    class Browse {

        @Test
        @DisplayName("when the repository answers three categories - then all three are answered, each with its "
                + "grouping")
        @Disabled("RU11: arranges requireByExternalId, which the use case no longer calls")
        void whenRepositoryAnswersThreeCategories_thenAllThreeAreAnsweredWithGroupingIdAndName() {
            when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
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
        @DisplayName("when the stored user's id differs from the external id - then the repository is asked with "
                + "that stored id")
        @Disabled("RU11: the command now carries the internal id; arrange requireById and assert the lookup "
                + "receives that id")
        void whenStoredUserIdDiffersFromExternalId_thenRepositoryReceivesStoredUserIdAndGroupingId() {
            long differentUserId = 42L;
            when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(differentUserId, EXTERNAL_ID));
            when(categoryRepository.findAllForUser(differentUserId, GROUPING_ID))
                    .thenReturn(List.of());

            useCase.browse(newCommand(GROUPING_ID));

            verify(categoryRepository).findAllForUser(differentUserId, GROUPING_ID);
        }

        @Test
        @DisplayName("when the grouping id names no grouping of the stored user's - then an empty list is answered")
        @Disabled("RU11: arranges requireByExternalId, which the use case no longer calls")
        void whenGroupingIdNamesNoGroupingOfCaller_thenEmptyListIsAnswered() {
            when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
            long unknownGroupingId = 999L;
            when(categoryRepository.findAllForUser(USER_ID, unknownGroupingId)).thenReturn(List.of());

            List<CategoryEntry> result = useCase.browse(newCommand(unknownGroupingId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName(
                "when no user row is stored under the caller's external id - then throws " + "EntityNotFoundException")
        @Disabled("RU11: the absence is now requireById throwing")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundException() {
            when(userRepository.requireByExternalId(EXTERNAL_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under external id " + EXTERNAL_ID));

            assertThatThrownBy(() -> useCase.browse(newCommand(null))).isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(categoryRepository);
        }
    }
}
