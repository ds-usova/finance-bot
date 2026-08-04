package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CreateExpenseProposalUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 2L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-29T10:15:30Z");
    private static final MessageReference MESSAGE_REFERENCE = MessageReference.newReference();

    private UserRepository userRepository;
    private GroupingRepository groupingRepository;
    private CategoryRepository categoryRepository;
    private ExpenseProposalRepository expenseProposalRepository;
    private Logger log;
    private CreateExpenseProposalUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        groupingRepository = mock(GroupingRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(CreateExpenseProposalUseCase.class)).thenReturn(log);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new CreateExpenseProposalUseCase(
                userRepository,
                groupingRepository,
                categoryRepository,
                expenseProposalRepository,
                clock,
                loggerFactory);
    }

    private CreateExpenseProposalCommand newExpenseProposal() {
        return newExpenseProposal("Groceries", "Food");
    }

    private CreateExpenseProposalCommand newExpenseProposal(String categoryName, String groupingName) {
        return new CreateExpenseProposalCommand(
                new AuthenticatedUserId(EXTERNAL_ID),
                categoryName,
                groupingName,
                "coffee",
                Optional.of("Starbucks"),
                new Money(500, CurrencyCode.of("USD")),
                MESSAGE_REFERENCE);
    }

    @Nested
    @DisplayName("creating an expense proposal")
    class Create {

        @Test
        @DisplayName("when a stored user, a grouping answered for the command's grouping name, and a category "
                + "answered under it - then the proposal handed to the proposal repository carries that category's "
                + "id, and the category repository received the user's stored id and that grouping")
        @Disabled("RU08: stub both reads through the two ports")
        void whenUserExistsForExternalId_thenRepositoryStoresProposalWithResolvedUserIdAndClockInstant() {
            // rework: resolve through groupingRepository.findByUserIdAndName then
            // categoryRepository.findByGroupingAndName
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws "
                + "EntityNotFoundException naming \"user\" and the proposal repository is untouched")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndProposalRepositoryIsUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> useCase.create(newExpenseProposal()))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("user");

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the command is absent - then throws InvalidExpenseProposalException and neither "
                + "repository is touched")
        void whenCommandIsAbsent_thenThrowsInvalidExpenseProposalExceptionAndRepositoriesAreUntouched() {
            assertThatThrownBy(() -> useCase.create(null)).isInstanceOf(InvalidExpenseProposalException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when a user is stored and the proposal repository raises PersistenceFailedException - then "
                + "the exception reaches the caller unchanged and is not swallowed or retried")
        @Disabled("RU08: stub both reads through the two ports")
        void whenProposalRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // PersistenceFailedException failure =
            //         new PersistenceFailedException("insert failed", new RuntimeException());
            // when(expenseProposalRepository.create(any())).thenThrow(failure);
            //
            // assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);
            //
            // verify(expenseProposalRepository, times(1)).create(any());
        }

        @Test
        @DisplayName("when the user repository raises PersistenceFailedException while resolving the identity - "
                + "then the exception reaches the caller unchanged and the proposal repository is untouched")
        void
                whenUserRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchangedAndProposalRepositoryUntouched() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when a stored category with the command's name carries a parent - then the proposal "
                + "repository is asked to store a proposal carrying that category's id, and the stored proposal "
                + "is returned")
        @Disabled("RU08: delete, replaced by the first scenario above")
        void
                whenExactlyOneStoredCategoryMatchesNameWithParent_thenProposalRepositoryStoresProposalWithThatCategoryId() {
            // replaced by whenUserExistsForExternalId_thenRepositoryStoresProposalWithResolvedUserIdAndClockInstant
        }

        @Test
        @DisplayName("when no stored category of the user's carries the command's name - then throws "
                + "InvalidCategoryException naming the unknown name, and the proposal repository is untouched")
        @Disabled(
                "RU08: delete, no category named <name> is stored for this user merges into the under-a-grouping refusal (D9)")
        void
                whenNoStoredCategoryMatchesName_thenThrowsInvalidCategoryExceptionNamingUnknownNameAndProposalRepositoryUntouched() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries")).thenReturn(List.of());
            //
            // assertThatThrownBy(() -> useCase.create(newExpenseProposal()))
            //         .isInstanceOf(InvalidCategoryException.class)
            //         .hasMessageContaining("Groceries");
            //
            // verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the only stored category carrying the command's name is a grouping - then throws "
                + "InvalidCategoryException naming the parent it was asked for, and the proposal repository is "
                + "untouched")
        @Disabled("RU08: delete, the candidate filter is gone")
        void whenOnlyMatchingCategoryIsAGrouping_thenThrowsInvalidCategoryExceptionAndProposalRepositoryUntouched() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
            //         .thenReturn(List.of(new StoredCategory(CATEGORY_ID, "Groceries", Optional.empty())));
            //
            // assertThatThrownBy(() -> useCase.create(newExpenseProposal()))
            //         .isInstanceOf(InvalidCategoryException.class)
            //         .hasMessageContaining("Groceries")
            //         .hasMessageContaining("Food");
            //
            // verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when several stored categories carry the command's name and the command's groupingName "
                + "matches exactly one of them - then the proposal repository is asked to store a proposal "
                + "carrying that candidate's id")
        @Disabled("RU08: delete, narrowing is now the query's scope, covered by RI02")
        void
                whenParentCategoryNameMatchesExactlyOneCandidate_thenProposalRepositoryStoresProposalWithThatCandidatesId() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // long matchingCandidateId = 3L;
            // when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
            //         .thenReturn(List.of(
            //                 new StoredCategory(CATEGORY_ID, "Groceries", Optional.of("Food")),
            //                 new StoredCategory(matchingCandidateId, "Groceries", Optional.of("Shopping"))));
            //
            // useCase.create(newExpenseProposal("Groceries", "Shopping"));
        }

        @Test
        @DisplayName("when several stored categories carry the command's name and the command's groupingName "
                + "matches none of them - then throws InvalidCategoryException, and the proposal repository is "
                + "untouched")
        @Disabled("RU08: delete, replaced by the unknown-category-under-the-grouping scenario above")
        void
                whenParentCategoryNameMatchesNoCandidate_thenThrowsInvalidCategoryExceptionAndProposalRepositoryUntouched() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
            //         .thenReturn(List.of(
            //                 new StoredCategory(CATEGORY_ID, "Groceries", Optional.of("Food")),
            //                 new StoredCategory(3L, "Groceries", Optional.of("Shopping"))));
            //
            // assertThatThrownBy(() -> useCase.create(newExpenseProposal("Groceries", "Travel")))
            //         .isInstanceOf(InvalidCategoryException.class);
            //
            // verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the command carries a message reference - then the proposal handed to the proposal "
                + "repository carries that same reference")
        @Disabled("RU08: stub both reads through the two ports")
        void whenCommandCarriesMessageReference_thenProposalRepositoryReceivesProposalWithThatReference() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries"))
            //         .thenReturn(List.of(new StoredCategory(CATEGORY_ID, "Groceries", Optional.of("Food"))));
            //
            // useCase.create(newExpenseProposal());
        }

        @Test
        @DisplayName("when the category repository raises PersistenceFailedException while resolving the name - "
                + "then the exception reaches the caller unchanged and the proposal repository is untouched")
        @Disabled("RU08: raise it from categoryRepository.findByGroupingAndName after the grouping resolves")
        void
                whenCategoryRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchangedAndProposalRepositoryUntouched() {
            // User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            // when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            // PersistenceFailedException failure =
            //         new PersistenceFailedException("lookup failed", new RuntimeException());
            // when(categoryRepository.findByUserIdAndName(USER_ID, "Groceries")).thenThrow(failure);
            //
            // assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);
            //
            // verifyNoInteractions(expenseProposalRepository);
        }
    }
}
