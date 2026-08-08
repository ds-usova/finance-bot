package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ExpensePage;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BrowseExpensesUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final long USER_ID = 1L;

    private UserRepository userRepository;
    private ExpenseRepository expenseRepository;
    private BrowseExpensesUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        Logger log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(BrowseExpensesUseCase.class)).thenReturn(log);
        useCase = new BrowseExpensesUseCase(userRepository, expenseRepository, loggerFactory);
    }

    private ExpenseFilter newFilter() {
        return new ExpenseFilter(ExpenseStatus.RECORDED, null, null, 20, 10);
    }

    private BrowseExpensesCommand newCommand(ExpenseFilter filter) {
        return new BrowseExpensesCommand(new AuthenticatedUserId(EXTERNAL_ID), filter);
    }

    @Nested
    @DisplayName("browsing expenses")
    class Browse {

        @Test
        @DisplayName("when the repository answers a page and a total - then the answered page carries them and "
                + "the filter's paging")
        void whenRepositoryAnswersPageAndTotal_thenAnsweredPageCarriesEntriesTotalLimitAndOffset() {
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            ExpenseFilter filter = newFilter();
            List<ExpenseEntry> entries = List.of(new ExpenseEntry(
                    ExpenseStatus.RECORDED,
                    1L,
                    2L,
                    "coffee",
                    Optional.empty(),
                    new Money(500, CurrencyCode.of("USD")),
                    Instant.parse("2026-08-01T00:00:00Z")));
            when(expenseRepository.findPage(USER_ID, filter)).thenReturn(entries);
            when(expenseRepository.countMatching(USER_ID, filter)).thenReturn(7L);

            ExpensePage result = useCase.browse(newCommand(filter));

            assertThat(result.items()).isEqualTo(entries);
            assertThat(result.total()).isEqualTo(7L);
            assertThat(result.limit()).isEqualTo(filter.limit());
            assertThat(result.offset()).isEqualTo(filter.offset());
        }

        @Test
        @DisplayName(
                "when the stored user's id differs from the external id - then both reads receive that " + "stored id")
        void whenStoredUserIdDiffersFromExternalId_thenBothReadsReceiveStoredUserIdNotExternalId() {
            long differentUserId = 42L;
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(differentUserId, EXTERNAL_ID)));
            ExpenseFilter filter = newFilter();
            when(expenseRepository.findPage(differentUserId, filter)).thenReturn(List.of());
            when(expenseRepository.countMatching(differentUserId, filter)).thenReturn(0L);

            useCase.browse(newCommand(filter));

            verify(expenseRepository).findPage(differentUserId, filter);
            verify(expenseRepository).countMatching(differentUserId, filter);
        }

        @Test
        @DisplayName("when no user is stored under the caller's external id - then throws EntityNotFoundException "
                + "and nothing is read")
        void whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndExpenseRepositoryUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.browse(newCommand(newFilter())))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when the expense repository raises PersistenceFailedException - then it reaches the caller "
                + "unchanged")
        void whenExpenseRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged() {
            when(userRepository.findByExternalId(EXTERNAL_ID))
                    .thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
            ExpenseFilter filter = newFilter();
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(expenseRepository.findPage(USER_ID, filter)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.browse(newCommand(filter))).isSameAs(failure);
        }
    }
}
