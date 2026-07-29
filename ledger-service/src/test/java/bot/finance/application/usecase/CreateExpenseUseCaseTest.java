package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.NewExpense;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.UnknownUserException;
import bot.finance.domain.model.Expense;
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

class CreateExpenseUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;
    private static final long CATEGORY_ID = 2L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-29T10:15:30Z");

    private UserRepository userRepository;
    private ExpenseRepository expenseRepository;
    private Logger log;
    private CreateExpenseUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(CreateExpenseUseCase.class)).thenReturn(log);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new CreateExpenseUseCase(userRepository, expenseRepository, clock, loggerFactory);
    }

    private NewExpense newExpense() {
        return new NewExpense(
                EXTERNAL_ID, CATEGORY_ID, "coffee", Optional.of("Starbucks"), new Money(500, CurrencyCode.of("USD")));
    }

    @Nested
    @DisplayName("creating an expense")
    class Create {

        @Test
        @DisplayName("when a user is stored under the command's external id and the clock is fixed at a known "
                + "instant - then the expense repository stores an expense carrying that user's database id and "
                + "the command's category id, description, merchant and money, with both timestamps equal to the "
                + "clock's instant, and the stored expense is returned")
        void whenUserExistsForExternalId_thenRepositoryStoresExpenseWithResolvedUserIdAndClockInstant() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            Expense createdExpense = Expense.stored(
                    10L,
                    USER_ID,
                    CATEGORY_ID,
                    "coffee",
                    Optional.of("Starbucks"),
                    new Money(500, CurrencyCode.of("USD")),
                    FIXED_INSTANT,
                    FIXED_INSTANT);
            when(expenseRepository.create(any())).thenReturn(createdExpense);

            Expense result = useCase.create(newExpense());

            ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
            verify(expenseRepository).create(expenseCaptor.capture());
            Expense stampedExpense = expenseCaptor.getValue();
            assertThat(stampedExpense.userId()).isEqualTo(USER_ID);
            assertThat(stampedExpense.categoryId()).isEqualTo(CATEGORY_ID);
            assertThat(stampedExpense.description()).isEqualTo("coffee");
            assertThat(stampedExpense.merchant()).contains("Starbucks");
            assertThat(stampedExpense.money()).isEqualTo(new Money(500, CurrencyCode.of("USD")));
            assertThat(stampedExpense.createdAt()).isEqualTo(FIXED_INSTANT);
            assertThat(stampedExpense.updatedAt()).isEqualTo(FIXED_INSTANT);
            assertThat(result).isSameAs(createdExpense);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws UnknownUserException "
                + "and the expense repository is untouched")
        void whenNoUserExistsForExternalId_thenThrowsUnknownUserExceptionAndExpenseRepositoryIsUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.create(newExpense())).isInstanceOf(UnknownUserException.class);

            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when the command is absent - then throws InvalidExpenseException and neither repository is "
                + "touched")
        void whenCommandIsAbsent_thenThrowsInvalidExpenseExceptionAndRepositoriesAreUntouched() {
            assertThatThrownBy(() -> useCase.create(null)).isInstanceOf(InvalidExpenseException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when a user is stored and the expense repository raises PersistenceFailedException - then "
                + "the exception reaches the caller unchanged and is not swallowed or retried")
        void whenExpenseRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            User storedUser = User.stored(USER_ID, EXTERNAL_ID);
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(storedUser));
            PersistenceFailedException failure =
                    new PersistenceFailedException("insert failed", new RuntimeException());
            when(expenseRepository.create(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpense())).isSameAs(failure);

            verify(expenseRepository, times(1)).create(any());
        }

        @Test
        @DisplayName("when the user repository raises PersistenceFailedException while resolving the identity - "
                + "then the exception reaches the caller unchanged and the expense repository is untouched")
        void
                whenUserRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchangedAndExpenseRepositoryUntouched() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpense())).isSameAs(failure);

            verifyNoInteractions(expenseRepository);
        }
    }
}
