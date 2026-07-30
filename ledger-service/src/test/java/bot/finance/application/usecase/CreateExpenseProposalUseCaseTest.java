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
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.model.User;
import bot.finance.domain.value.CurrencyCode;
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
    private static final long CATEGORY_ID = 2L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-29T10:15:30Z");

    private UserRepository userRepository;
    private ExpenseProposalRepository expenseProposalRepository;
    private Logger log;
    private CreateExpenseProposalUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(CreateExpenseProposalUseCase.class)).thenReturn(log);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new CreateExpenseProposalUseCase(userRepository, expenseProposalRepository, clock, loggerFactory);
    }

    private CreateExpenseProposalCommand newExpenseProposal() {
        return new CreateExpenseProposalCommand(
                EXTERNAL_ID, CATEGORY_ID, "coffee", Optional.of("Starbucks"), new Money(500, CurrencyCode.of("USD")));
    }

    @Nested
    @DisplayName("creating an expense proposal")
    class Create {

        @Test
        @DisplayName("when a user is stored under the command's external id and the clock is fixed at a known "
                + "instant - then the proposal repository stores a proposal carrying that user's database id and "
                + "the command's category id, description, merchant and money, with both timestamps equal to the "
                + "clock's instant, and the stored proposal is returned")
        void whenUserExistsForExternalId_thenRepositoryStoresProposalWithResolvedUserIdAndClockInstant() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            ExpenseProposal createdProposal = ExpenseProposal.stored(
                    10L,
                    USER_ID,
                    CATEGORY_ID,
                    "coffee",
                    Optional.of("Starbucks"),
                    new Money(500, CurrencyCode.of("USD")),
                    FIXED_INSTANT,
                    FIXED_INSTANT);
            when(expenseProposalRepository.create(any())).thenReturn(createdProposal);

            ExpenseProposal result = useCase.create(newExpenseProposal());

            ArgumentCaptor<ExpenseProposal> proposalCaptor = ArgumentCaptor.forClass(ExpenseProposal.class);
            verify(expenseProposalRepository).create(proposalCaptor.capture());
            ExpenseProposal stampedProposal = proposalCaptor.getValue();
            assertThat(stampedProposal.userId()).isEqualTo(USER_ID);
            assertThat(stampedProposal.categoryId()).isEqualTo(CATEGORY_ID);
            assertThat(stampedProposal.description()).isEqualTo("coffee");
            assertThat(stampedProposal.merchant()).contains("Starbucks");
            assertThat(stampedProposal.money()).isEqualTo(new Money(500, CurrencyCode.of("USD")));
            assertThat(stampedProposal.createdAt()).isEqualTo(FIXED_INSTANT);
            assertThat(stampedProposal.updatedAt()).isEqualTo(FIXED_INSTANT);
            assertThat(result).isSameAs(createdProposal);
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

            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when the command is absent - then throws InvalidExpenseProposalException and neither "
                + "repository is touched")
        void whenCommandIsAbsent_thenThrowsInvalidExpenseProposalExceptionAndRepositoriesAreUntouched() {
            assertThatThrownBy(() -> useCase.create(null)).isInstanceOf(InvalidExpenseProposalException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(expenseProposalRepository);
        }

        @Test
        @DisplayName("when a user is stored and the proposal repository raises PersistenceFailedException - then "
                + "the exception reaches the caller unchanged and is not swallowed or retried")
        void whenProposalRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            PersistenceFailedException failure =
                    new PersistenceFailedException("insert failed", new RuntimeException());
            when(expenseProposalRepository.create(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpenseProposal())).isSameAs(failure);

            verify(expenseProposalRepository, times(1)).create(any());
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

            verifyNoInteractions(expenseProposalRepository);
        }
    }
}
