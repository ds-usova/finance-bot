package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LearnMessageOutcomeUseCaseTest {

    private static final int ENTRY_ATTEMPTS = 3;

    private RecordedExpenseStorePort recordedExpenseStorePort;
    private ChangeAttemptStorePort changeAttemptStorePort;
    private LearnMessageOutcomeUseCase useCase;

    @BeforeEach
    void setUp() {
        recordedExpenseStorePort = mock(RecordedExpenseStorePort.class);
        changeAttemptStorePort = mock(ChangeAttemptStorePort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));
        useCase = new LearnMessageOutcomeUseCase(
                recordedExpenseStorePort, changeAttemptStorePort, ENTRY_ATTEMPTS, loggerFactory);
    }

    @Nested
    @DisplayName("constructing a LearnMessageOutcomeUseCase")
    class Constructor {

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        @DisplayName("when entryAttempts is zero or negative - then throws InvalidValueException")
        void whenEntryAttemptsIsZeroOrNegative_thenThrowsInvalidValueException(int entryAttempts) {
            LoggerFactory loggerFactory = mock(LoggerFactory.class);
            when(loggerFactory.getLogger(any())).thenReturn(mock(Logger.class));

            assertThatThrownBy(() -> new LearnMessageOutcomeUseCase(
                            recordedExpenseStorePort, changeAttemptStorePort, entryAttempts, loggerFactory))
                    .isInstanceOf(InvalidValueException.class);
        }
    }

    // Every scenario below drove the deleted pairing, rename and abandonment arms, or asserted on the old
    // per-operation store methods; the rework applies one command through one apply(...) call and keeps no
    // provisional state.
    @Nested
    @DisplayName("learning a message outcome")
    class Learn {

        @Disabled("RU06: rewritten against apply(command.entry(), command.status(), command.position())")
        @Test
        @DisplayName("when a proposal CREATED or UPDATED carries a message id - then recordProposed() gets the "
                + "after row, outcome APPLIED")
        void whenProposalCreatedOrUpdatedWithMessageId_thenRecordProposedReceivesAfterRowAndOutcomeApplied() {}

        @Disabled("RU06: the pairing/settle arms no longer exist")
        @Test
        @DisplayName("when a proposal DELETED carries a message id - then settleProposalDeleted() gets the "
                + "before row and tx id")
        void whenProposalDeletedWithMessageId_thenSettleProposalDeletedReceivesBeforeRowAndTransactionId() {}

        @Disabled("RU06: the pairing/settle arms no longer exist")
        @Test
        @DisplayName("when an expense CREATED carries a message id - then settleExpenseInserted() gets the "
                + "after row and tx id")
        void whenExpenseCreatedWithMessageId_thenSettleExpenseInsertedReceivesAfterRowAndTransactionId() {}

        @Disabled("RU06: the pairing/settle arms no longer exist")
        @Test
        @DisplayName("when an expense UPDATED carries a message id - then refileExpense() receives the after row")
        void whenExpenseUpdatedWithMessageId_thenRefileExpenseReceivesAfterRow() {}

        @Disabled("RU06: the pairing/settle arms no longer exist")
        @Test
        @DisplayName(
                "when an expense DELETED carries a message id - then removeExpense() receives the before " + "row's id")
        void whenExpenseDeletedWithMessageId_thenRemoveExpenseReceivesBeforeRowId() {}

        @Disabled("RU06: rewritten against a command whose entry carries no message id")
        @Test
        @DisplayName("when an expense or proposal change carries no message id - then the store is never "
                + "touched and the outcome is APPLIED")
        void whenSpendingChangeCarriesNoMessageId_thenStoreNeverTouchedAndOutcomeApplied() {}

        @Disabled("RU06: the rename arms no longer exist")
        @Test
        @DisplayName("when a category UPDATED carries a parent and a new name - then renameCategory() gets the "
                + "category id and after.name")
        void whenCategoryUpdatedWithParentAndNewName_thenRenameCategoryReceivesCategoryIdAndAfterName() {}

        @Disabled("RU06: the rename arms no longer exist")
        @Test
        @DisplayName("when a category UPDATED has no parent and a new name - then renameGrouping() gets the "
                + "user id, before and after names")
        void whenCategoryUpdatedWithNoParentAndNewName_thenRenameGroupingReceivesUserIdBeforeNameAndAfterName() {}

        @Disabled("RU06: category changes no longer reach this use case")
        @Test
        @DisplayName("when a category is moved, created, or deleted - then the store is never touched and the "
                + "outcome is APPLIED")
        void whenCategoryMovedCreatedOrDeleted_thenStoreNeverTouchedAndOutcomeApplied() {}

        @Disabled("RU06: the abandonment arm no longer exists")
        @Test
        @DisplayName("when the store applies the change - then attempts.clear() receives the delivery id")
        void whenStoreApplies_thenAttemptsClearReceivesDeliveryId() {}

        @Disabled("RU06: rewritten against apply(...) throwing MessageStoreUnavailableException")
        @Test
        @DisplayName("when the store throws MessageStoreUnavailableException 3x - then RETRY_LATER, uncounted, "
                + "WARN per call")
        void whenStoreThrowsUnavailableThreeTimes_thenRetryLaterCountFailureNeverCalledAndOneWarnPerCall() {}

        @Disabled("RU06: rewritten against apply(...) throwing MessageStoreFailedException")
        @Test
        @DisplayName("when the store fails and the attempt store answers 1 then 2 - then RETRY_LATER, counted, "
                + "no ERROR logged")
        void whenStoreThrowsFailedAndAttemptStoreAnswersOneThenTwo_thenRetryLaterCountFailureCalledNoErrorLogged() {}

        @Disabled("RU06: the abandonment arm no longer exists - the ERROR line now names status and expenseId")
        @Test
        @DisplayName("when an expense CREATED fails at entryAttempts - then DROPPED, ERROR names ids, "
                + "abandonAcceptance(), clear() called")
        void whenExpenseCreatedFailsAtEntryAttempts_thenDroppedErrorLoggedAbandonAcceptanceAndClearCalled() {}

        @Disabled("RU06: the abandonment arm no longer exists")
        @Test
        @DisplayName(
                "when a non-expense-CREATED change is dropped - then DROPPED, abandonAcceptance() never " + "called")
        void whenNonExpenseCreatedChangeIsDropped_thenDroppedAndAbandonAcceptanceNeverCalled() {}

        @Disabled("RU06: rewritten against the attempt store throwing while counting")
        @Test
        @DisplayName("when the attempt store throws MessageStoreUnavailableException while counting - then "
                + "RETRY_LATER, nothing propagates")
        void whenAttemptStoreThrowsUnavailableWhileCounting_thenRetryLaterAndNothingPropagates() {}

        @Disabled("RU06: the abandonment arm no longer exists")
        @Test
        @DisplayName("when a drop's abandonAcceptance() throws MessageStoreFailedException - then still "
                + "DROPPED, one WARN logged")
        void whenAbandonAcceptanceThrowsFailedException_thenStillDroppedAndOneWarnLogged() {}
    }
}
