package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ChangeExpenseCategoryCommand;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.ExpenseEntryNotFoundException;
import bot.finance.domain.exception.InvalidExpenseCategoryChangeException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
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

class ChangeExpenseCategoryUseCaseTest {

    private static final String EXTERNAL_ID = "555";
    private static final AuthenticatedUserId CALLER = new AuthenticatedUserId(1L);
    private static final long USER_ID = 1L;
    private static final long ENTRY_ID = 10L;
    private static final long CATEGORY_ID = 2L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-29T10:15:30Z");

    private UserRepository userRepository;
    private CategoryRepository categoryRepository;
    private ExpenseRepository expenseRepository;
    private ChangeExpenseCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        Logger log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(ChangeExpenseCategoryUseCase.class)).thenReturn(log);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new ChangeExpenseCategoryUseCase(
                userRepository, categoryRepository, expenseRepository, clock, loggerFactory);
    }

    @Nested
    @DisplayName("changing an expense's category")
    class Change {

        @Test
        @DisplayName(
                "when the command names RECORDED - then refile is called with RECORDED and the entry is " + "returned")
        void whenCommandNamesRecordedAndStoreAnswersRefiledEntry_thenRefileCalledOnceWithRecordedAndEntryReturned() {
            stubStoredUser();
            stubCategoryAdmitted();
            ExpenseEntry entry = newEntry(CATEGORY_ID);
            when(expenseRepository.refile(USER_ID, ENTRY_ID, CATEGORY_ID, FIXED_INSTANT))
                    .thenReturn(Optional.of(entry));

            ExpenseEntry result = useCase.change(newCommand());

            assertThat(result).isSameAs(entry);
            verify(expenseRepository).refile(USER_ID, ENTRY_ID, CATEGORY_ID, FIXED_INSTANT);
        }

        @Test
        @DisplayName(
                "when the command names PENDING - then refile is called with PENDING and the entry is " + "returned")
        @Disabled("R01: the command no longer carries status; merged with the RECORDED case by R02")
        void whenCommandNamesPendingAndStoreAnswersRefiledEntry_thenRefileCalledOnceWithPendingAndEntryReturned() {
            //            stubStoredUser();
            //            stubCategoryAdmitted();
            //            ExpenseEntry entry = newEntry(CATEGORY_ID);
            //            when(expenseRepository.refile(USER_ID, ENTRY_ID, CATEGORY_ID, ExpenseStatus.PENDING,
            // FIXED_INSTANT))
            //                    .thenReturn(Optional.of(entry));
            //
            //            ExpenseEntry result = useCase.change(newCommand(ExpenseStatus.PENDING));
            //
            //            assertThat(result).isSameAs(entry);
            //            verify(expenseRepository).refile(USER_ID, ENTRY_ID, CATEGORY_ID, ExpenseStatus.PENDING,
            // FIXED_INSTANT);
        }

        @Test
        @DisplayName("when the category read does not admit categoryId - then throws "
                + "InvalidExpenseCategoryChangeException naming it")
        void whenCategoryReadDoesNotAdmitCategoryId_thenThrowsInvalidExpenseCategoryChangeExceptionNamingCategoryId() {
            stubStoredUser();
            when(categoryRepository.existsOwnedCategory(USER_ID, CATEGORY_ID)).thenReturn(false);

            assertThatThrownBy(() -> useCase.change(newCommand()))
                    .isInstanceOf(InvalidExpenseCategoryChangeException.class)
                    .hasMessageContaining("categoryId");

            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when the refile answers nothing - then throws ExpenseEntryNotFoundException naming the "
                + "entry rather than the caller")
        void whenRefileAnswersNothing_thenThrowsExpenseEntryNotFoundExceptionNamingEntryRatherThanCaller() {
            stubStoredUser();
            stubCategoryAdmitted();
            when(expenseRepository.refile(USER_ID, ENTRY_ID, CATEGORY_ID, FIXED_INSTANT))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.change(newCommand()))
                    .isInstanceOf(ExpenseEntryNotFoundException.class)
                    .hasMessageContaining("entry")
                    .hasMessageNotContaining("user")
                    .hasMessageNotContaining(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when no user row is stored for the caller's external id - then EntityNotFoundException "
                + "propagates")
        void whenNoUserRowStoredForExternalId_thenEntityNotFoundExceptionPropagatesAndNothingElseTouched() {
            EntityNotFoundException failure = new EntityNotFoundException("user", "no user stored");
            when(userRepository.requireById(USER_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.change(newCommand())).isSameAs(failure);

            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when the category read throws PersistenceFailedException - then it propagates and neither "
                + "refile is attempted")
        void whenCategoryReadThrowsPersistenceFailedException_thenPropagatesAndNeitherRefileAttempted() {
            stubStoredUser();
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(categoryRepository.existsOwnedCategory(USER_ID, CATEGORY_ID)).thenThrow(failure);

            assertThatThrownBy(() -> useCase.change(newCommand())).isSameAs(failure);

            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when a refile throws PersistenceFailedException - then it propagates")
        void whenRefileThrowsPersistenceFailedException_thenPropagates() {
            stubStoredUser();
            stubCategoryAdmitted();
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            when(expenseRepository.refile(USER_ID, ENTRY_ID, CATEGORY_ID, FIXED_INSTANT))
                    .thenThrow(failure);

            assertThatThrownBy(() -> useCase.change(newCommand())).isSameAs(failure);
        }

        @Test
        @DisplayName("when the command is absent - then throws InvalidExpenseCategoryChangeException and no port "
                + "is touched")
        void whenCommandIsAbsent_thenThrowsInvalidExpenseCategoryChangeExceptionAndNoPortTouched() {
            assertThatThrownBy(() -> useCase.change(null)).isInstanceOf(InvalidExpenseCategoryChangeException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(categoryRepository);
            verifyNoInteractions(expenseRepository);
        }
    }

    private ChangeExpenseCategoryCommand newCommand() {
        return new ChangeExpenseCategoryCommand(CALLER, ENTRY_ID, CATEGORY_ID);
    }

    private void stubStoredUser() {
        when(userRepository.requireById(USER_ID)).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
    }

    private void stubCategoryAdmitted() {
        when(categoryRepository.existsOwnedCategory(USER_ID, CATEGORY_ID)).thenReturn(true);
    }

    private ExpenseEntry newEntry(long categoryId) {
        return new ExpenseEntry(
                ExpenseStatus.RECORDED,
                ENTRY_ID,
                categoryId,
                "coffee",
                Optional.of("Starbucks"),
                new Money(500, CurrencyCode.of("USD")),
                FIXED_INSTANT);
    }
}
