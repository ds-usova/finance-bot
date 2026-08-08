package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.application.dto.StoredCategory;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateExpenseProposalUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long GROUPING_ID = 3L;
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

    /** Stores a user and answers the command's grouping name. */
    private StoredGrouping stubResolvedGrouping() {
        when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
        StoredGrouping storedGrouping = new StoredGrouping(GROUPING_ID, "Food");
        when(groupingRepository.findByUserIdAndName(USER_ID, "Food")).thenReturn(Optional.of(storedGrouping));
        return storedGrouping;
    }

    /** Stores a user, answers the command's grouping name and answers a category under it. */
    private StoredGrouping stubResolvedGroupingAndCategory() {
        StoredGrouping storedGrouping = stubResolvedGrouping();
        when(categoryRepository.findByGroupingAndName(USER_ID, storedGrouping, "Groceries"))
                .thenReturn(Optional.of(new StoredCategory(CATEGORY_ID, "Groceries")));
        return storedGrouping;
    }

    private ExpenseProposal capturedProposal() {
        ArgumentCaptor<ExpenseProposal> proposalCaptor = ArgumentCaptor.forClass(ExpenseProposal.class);
        verify(expenseProposalRepository).create(proposalCaptor.capture());
        return proposalCaptor.getValue();
    }

    @Nested
    @DisplayName("creating an expense proposal")
    class Create {

        @Test
        @DisplayName("when a grouping and a category are answered - then the proposal carries that category's id")
        void whenGroupingAndCategoryAreAnswered_thenProposalCarriesThatCategorysId() {
            stubResolvedGroupingAndCategory();

            useCase.create(newExpenseProposal());

            assertThat(capturedProposal().categoryId()).isEqualTo(CATEGORY_ID);
        }

        @Test
        @DisplayName("when a grouping is answered - then the category is looked up under the user's stored id "
                + "and that grouping")
        void whenGroupingIsAnswered_thenCategoryIsLookedUpUnderStoredUserIdAndThatGrouping() {
            StoredGrouping storedGrouping = stubResolvedGroupingAndCategory();

            useCase.create(newExpenseProposal());

            verify(categoryRepository).findByGroupingAndName(USER_ID, storedGrouping, "Groceries");
        }

        @Test
        @DisplayName("when the proposal repository stores the proposal - then it is returned to the caller")
        void whenProposalRepositoryStoresTheProposal_thenItIsReturnedToTheCaller() {
            stubResolvedGroupingAndCategory();
            ExpenseProposal createdProposal = ExpenseProposal.stored(
                    10L,
                    USER_ID,
                    CATEGORY_ID,
                    "coffee",
                    Optional.of("Starbucks"),
                    new Money(500, CurrencyCode.of("USD")),
                    MESSAGE_REFERENCE,
                    FIXED_INSTANT,
                    FIXED_INSTANT);
            when(expenseProposalRepository.create(any())).thenReturn(createdProposal);

            ExpenseProposal result = useCase.create(newExpenseProposal());

            assertThat(result).isSameAs(createdProposal);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws "
                + "EntityNotFoundException naming \"user\"")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionNamingUser() {
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
        @DisplayName("when the proposal repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenProposalRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            stubResolvedGroupingAndCategory();
            PersistenceFailedException failure =
                    new PersistenceFailedException("insert failed", new RuntimeException());
            when(expenseProposalRepository.create(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);

            verify(expenseProposalRepository, times(1)).create(any());
        }

        @Test
        @DisplayName("when the user repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenUserRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);

            verifyNoInteractions(groupingRepository);
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the grouping repository answers nothing - then throws InvalidGroupingException naming "
                + "the grouping")
        void whenGroupingRepositoryAnswersNothing_thenThrowsInvalidGroupingExceptionNamingTheGrouping() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            when(groupingRepository.findByUserIdAndName(USER_ID, "Food")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.create(newExpenseProposal()))
                    .isInstanceOf(InvalidGroupingException.class)
                    .hasMessageContaining("Food")
                    .hasMessageContaining("no grouping named");

            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the category repository answers nothing - then throws InvalidCategoryException naming "
                + "the category and grouping")
        void whenCategoryRepositoryAnswersNothing_thenThrowsInvalidCategoryExceptionNamingCategoryAndGrouping() {
            StoredGrouping storedGrouping = stubResolvedGrouping();
            when(categoryRepository.findByGroupingAndName(USER_ID, storedGrouping, "Groceries"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.create(newExpenseProposal()))
                    .isInstanceOf(InvalidCategoryException.class)
                    .hasMessageContaining("Groceries")
                    .hasMessageContaining("Food");

            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the grouping repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenGroupingRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(groupingRepository.findByUserIdAndName(USER_ID, "Food")).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);

            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName(
                "when the command carries a message reference - then the stored proposal carries that " + "reference")
        void whenCommandCarriesMessageReference_thenProposalRepositoryReceivesProposalWithThatReference() {
            stubResolvedGroupingAndCategory();

            useCase.create(newExpenseProposal());

            assertThat(capturedProposal().messageReference()).isEqualTo(MESSAGE_REFERENCE);
        }

        @Test
        @DisplayName("when the category repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenCategoryRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            StoredGrouping storedGrouping = stubResolvedGrouping();
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(categoryRepository.findByGroupingAndName(USER_ID, storedGrouping, "Groceries"))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);

            verifyNoInteractions(expenseProposalRepository);
        }
    }
}
