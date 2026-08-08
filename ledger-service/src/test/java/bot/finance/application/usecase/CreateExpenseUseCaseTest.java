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

import bot.finance.application.dto.CreateExpenseCommand;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
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

    private CreateExpenseCommand newExpense() {
        return new CreateExpenseCommand(
                EXTERNAL_ID, CATEGORY_ID, "coffee", Optional.of("Starbucks"), new Money(500, CurrencyCode.of("USD")));
    }

    private void stubStoredUser() {
        when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
    }

    private Expense capturedExpense() {
        ArgumentCaptor<Expense> expenseCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).create(expenseCaptor.capture());
        return expenseCaptor.getValue();
    }

    @Nested
    @DisplayName("creating an expense")
    class Create {

        @Test
        @DisplayName("when a user is stored under the external id - then the expense carries that user's id and "
                + "the command's fields")
        void whenUserExistsForExternalId_thenExpenseCarriesStoredUserIdAndCommandFields() {
            stubStoredUser();

            useCase.create(newExpense());

            Expense stampedExpense = capturedExpense();
            assertThat(stampedExpense.userId()).isEqualTo(USER_ID);
            assertThat(stampedExpense.categoryId()).isEqualTo(CATEGORY_ID);
            assertThat(stampedExpense.description()).isEqualTo("coffee");
            assertThat(stampedExpense.merchant()).contains("Starbucks");
            assertThat(stampedExpense.money()).isEqualTo(new Money(500, CurrencyCode.of("USD")));
        }

        @Test
        @DisplayName("when the clock is fixed at a known instant - then the expense is stamped with it, created "
                + "and updated")
        void whenClockIsFixedAtAKnownInstant_thenExpenseIsStampedWithIt() {
            stubStoredUser();

            useCase.create(newExpense());

            Expense stampedExpense = capturedExpense();
            assertThat(stampedExpense.createdAt()).isEqualTo(FIXED_INSTANT);
            assertThat(stampedExpense.updatedAt()).isEqualTo(FIXED_INSTANT);
        }

        @Test
        @DisplayName("when the expense repository stores the expense - then it is returned to the caller")
        void whenExpenseRepositoryStoresTheExpense_thenItIsReturnedToTheCaller() {
            stubStoredUser();
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

            assertThat(result).isSameAs(createdExpense);
        }

        @Test
        @DisplayName("when nothing is stored under the command's external id - then throws "
                + "EntityNotFoundException naming \"user\"")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionNamingUser() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> useCase.create(newExpense()))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("user");

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
        @DisplayName("when the expense repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenExpenseRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            stubStoredUser();
            PersistenceFailedException failure =
                    new PersistenceFailedException("insert failed", new RuntimeException());
            when(expenseRepository.create(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpense())).isSameAs(failure);

            verify(expenseRepository, times(1)).create(any());
        }

        @Test
        @DisplayName("when the user repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenUserRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.create(newExpense())).isSameAs(failure);

            verifyNoInteractions(expenseRepository);
        }
    }
}
