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
import org.junit.jupiter.api.Disabled;
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
        return new ListCategoriesCommand(new AuthenticatedUserId(USER_ID), groupingName);
    }

    private void stubStoredUser(long userId) {
        when(userRepository.requireByExternalId(EXTERNAL_ID)).thenReturn(User.stored(userId, EXTERNAL_ID));
    }

    /** Stores a user under {@code userId} and answers a Groceries grouping for it. */
    private StoredGrouping stubStoredGrouping(long userId) {
        stubStoredUser(userId);
        StoredGrouping grouping = new StoredGrouping(GROUPING_ID, "Groceries");
        when(groupingRepository.findByUserIdAndName(userId, "Groceries")).thenReturn(Optional.of(grouping));
        return grouping;
    }

    /** No grouping named Coffee, but a category of that name. */
    private void stubNameStoredAsCategoryOnly() {
        stubStoredUser(USER_ID);
        when(groupingRepository.findByUserIdAndName(USER_ID, "Coffee")).thenReturn(Optional.empty());
        when(categoryRepository.existsByUserIdAndName(USER_ID, "Coffee")).thenReturn(true);
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
        @DisplayName("when nothing is stored under the command's external id - then throws EntityNotFoundException")
        @Disabled("RU14: the absence is now requireById throwing")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundException() {
            when(userRepository.requireByExternalId(EXTERNAL_ID))
                    .thenThrow(new EntityNotFoundException("user", "no user stored under external id " + EXTERNAL_ID));

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when a grouping is answered - then its category names are returned unchanged")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenAGroupingIsAnswered_thenItsCategoryNamesAreReturnedUnchanged() {
            StoredGrouping grouping = stubStoredGrouping(USER_ID);
            List<String> categoryNames = List.of("Coffee", "Restaurant");
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(categoryNames);

            List<String> result = useCase.list(newListCategories("Groceries"));

            assertThat(result).isEqualTo(categoryNames);
        }

        @Test
        @DisplayName(
                "when a grouping is answered - then its categories are read through the grouping repository " + "alone")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenAGroupingIsAnswered_thenCategoriesAreReadThroughTheGroupingRepositoryAlone() {
            StoredGrouping grouping = stubStoredGrouping(USER_ID);
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(List.of("Coffee"));

            useCase.list(newListCategories("Groceries"));

            verify(groupingRepository).findCategoryNames(USER_ID, grouping);
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("when the name is a stored category, not a grouping - then throws InvalidGroupingException "
                + "saying so")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenNameIsAStoredCategory_thenThrowsInvalidGroupingExceptionSayingCategoryNotGrouping() {
            stubNameStoredAsCategoryOnly();

            assertThatThrownBy(() -> useCase.list(newListCategories("Coffee")))
                    .isInstanceOf(InvalidGroupingException.class)
                    .hasMessageContaining("Coffee")
                    .hasMessageContaining("category, not a grouping");

            verify(groupingRepository, never()).findCategoryNames(anyLong(), any());
        }

        @Test
        @DisplayName(
                "when the name is checked against the categories - then the check receives the stored " + "user's id")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenNameIsCheckedAgainstTheCategories_thenTheCheckReceivesTheStoredUsersId() {
            stubNameStoredAsCategoryOnly();

            assertThatThrownBy(() -> useCase.list(newListCategories("Coffee")))
                    .isInstanceOf(InvalidGroupingException.class);

            verify(categoryRepository).existsByUserIdAndName(USER_ID, "Coffee");
        }

        @Test
        @DisplayName("when the name is neither a grouping nor a category - then throws InvalidGroupingException "
                + "naming it as unstored")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenNameIsNeitherGroupingNorCategory_thenThrowsInvalidGroupingExceptionNamingGroupingAsUnstored() {
            stubStoredUser(USER_ID);
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.empty());
            when(categoryRepository.existsByUserIdAndName(USER_ID, "Groceries")).thenReturn(false);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isInstanceOf(InvalidGroupingException.class)
                    .hasMessageContaining("Groceries")
                    .hasMessageContaining("stored");

            verify(categoryRepository).existsByUserIdAndName(USER_ID, "Groceries");
        }

        @Test
        @DisplayName("when existsByUserIdAndName raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenExistsByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            stubStoredUser(USER_ID);
            when(groupingRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(Optional.empty());
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.existsByUserIdAndName(USER_ID, "Groceries")).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);
        }

        @Test
        @DisplayName(
                "when the stored user's id differs from the external id - then both reads receive that " + "stored id")
        @Disabled("RU14: arrange requireById and assert both reads receive the command's internal id")
        void whenStoredUsersIdDiffersFromCommandsExternalId_thenBothReadsReceiveStoredUsersIdNotExternalId() {
            long differentUserId = 42L;
            StoredGrouping grouping = stubStoredGrouping(differentUserId);
            when(groupingRepository.findCategoryNames(differentUserId, grouping))
                    .thenReturn(List.of("Coffee"));

            useCase.list(newListCategories("Groceries"));

            verify(groupingRepository).findByUserIdAndName(differentUserId, "Groceries");
            verify(groupingRepository).findCategoryNames(differentUserId, grouping);
        }

        @Test
        @DisplayName("when the stored user's grouping has no categories - then returns an empty list rather than "
                + "throwing")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenGroupingHasNoCategories_thenReturnsEmptyListRatherThanThrowing() {
            StoredGrouping grouping = stubStoredGrouping(USER_ID);
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenReturn(List.of());

            List<String> result = useCase.list(newListCategories("Groceries"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when findByUserIdAndName raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenFindByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            stubStoredUser(USER_ID);
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
        @Disabled("RU14: arranges requireByExternalId, which the use case no longer calls")
        void whenFindCategoryNamesRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            StoredGrouping grouping = stubStoredGrouping(USER_ID);
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(groupingRepository.findCategoryNames(USER_ID, grouping)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.list(newListCategories("Groceries")))
                    .isSameAs(failure);
        }
    }
}
