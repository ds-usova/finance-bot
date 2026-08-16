package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.common.fixtures.RecordedChangeFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import bot.finance.ai.domain.value.CategoryRow;
import bot.finance.ai.domain.value.CategoryRowChange;
import bot.finance.ai.domain.value.ChangeOperation;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.RecordedChange;
import bot.finance.ai.domain.value.SpendingKind;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.SpendingRowChange;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LearnMessageOutcomeUseCaseTest {

    private static final String DELIVERY_ID = "delivery-1";
    private static final int ENTRY_ATTEMPTS = 3;

    private RecordedExpenseStorePort recordedExpenseStorePort;
    private ChangeAttemptStorePort changeAttemptStorePort;
    private Logger log;
    private LearnMessageOutcomeUseCase useCase;

    @BeforeEach
    void setUp() {
        recordedExpenseStorePort = mock(RecordedExpenseStorePort.class);
        changeAttemptStorePort = mock(ChangeAttemptStorePort.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        log = mock(Logger.class);
        when(loggerFactory.getLogger(any())).thenReturn(log);
        useCase = new LearnMessageOutcomeUseCase(
                recordedExpenseStorePort, changeAttemptStorePort, ENTRY_ATTEMPTS, loggerFactory);
    }

    private static LearnMessageOutcomeCommand command(RecordedChange change) {
        return new LearnMessageOutcomeCommand(DELIVERY_ID, change);
    }

    private List<String> loggedWarnLines() {
        return MockedLoggerUtils.warnLines(log);
    }

    private List<String> loggedErrorLines() {
        return MockedLoggerUtils.linesAt(log, "error");
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

    @Nested
    @DisplayName("learning a message outcome")
    class Learn {

        @ParameterizedTest
        @MethodSource("proposalCreatedOrUpdated")
        @DisplayName("when a proposal CREATED or UPDATED carries a message id - then recordProposed() gets the "
                + "after row, outcome APPLIED")
        void whenProposalCreatedOrUpdatedWithMessageId_thenRecordProposedReceivesAfterRowAndOutcomeApplied(
                SpendingRowChange change) {
            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verify(recordedExpenseStorePort).recordProposed(eq(change.after().orElseThrow()));
        }

        private static Stream<SpendingRowChange> proposalCreatedOrUpdated() {
            return Stream.of(RecordedChangeFixtures.proposalCreated(), RecordedChangeFixtures.proposalUpdated());
        }

        @Test
        @DisplayName("when a proposal DELETED carries a message id - then settleProposalDeleted() gets the "
                + "before row and tx id")
        void whenProposalDeletedWithMessageId_thenSettleProposalDeletedReceivesBeforeRowAndTransactionId() {
            SpendingRowChange change = RecordedChangeFixtures.proposalDeleted();

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verify(recordedExpenseStorePort)
                    .settleProposalDeleted(eq(change.before().orElseThrow()), eq(change.transactionId()));
        }

        @Test
        @DisplayName("when an expense CREATED carries a message id - then settleExpenseInserted() gets the "
                + "after row and tx id")
        void whenExpenseCreatedWithMessageId_thenSettleExpenseInsertedReceivesAfterRowAndTransactionId() {
            SpendingRowChange change = RecordedChangeFixtures.expenseCreated();

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verify(recordedExpenseStorePort)
                    .settleExpenseInserted(eq(change.after().orElseThrow()), eq(change.transactionId()));
        }

        @Test
        @DisplayName("when an expense UPDATED carries a message id - then refileExpense() receives the after row")
        void whenExpenseUpdatedWithMessageId_thenRefileExpenseReceivesAfterRow() {
            SpendingRowChange change = RecordedChangeFixtures.expenseUpdated();

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verify(recordedExpenseStorePort).refileExpense(eq(change.after().orElseThrow()));
        }

        @Test
        @DisplayName(
                "when an expense DELETED carries a message id - then removeExpense() receives the before " + "row's id")
        void whenExpenseDeletedWithMessageId_thenRemoveExpenseReceivesBeforeRowId() {
            SpendingRowChange change = RecordedChangeFixtures.expenseDeleted();

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verify(recordedExpenseStorePort)
                    .removeExpense(eq(change.before().orElseThrow().id()));
        }

        @ParameterizedTest
        @MethodSource("spendingChangesWithNoMessageId")
        @DisplayName("when an expense or proposal change carries no message id - then the store is never "
                + "touched and the outcome is APPLIED")
        void whenSpendingChangeCarriesNoMessageId_thenStoreNeverTouchedAndOutcomeApplied(SpendingRowChange change) {
            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verifyNoInteractions(recordedExpenseStorePort);
        }

        private static Stream<SpendingRowChange> spendingChangesWithNoMessageId() {
            SpendingRow row =
                    RecordedChangeFixtures.spendingRow(RecordedChangeFixtures.DEFAULT_ROW_ID, Optional.empty());
            String tx = RecordedChangeFixtures.DEFAULT_TRANSACTION_ID;
            return Stream.of(
                    new SpendingRowChange(
                            SpendingKind.EXPENSE, ChangeOperation.CREATED, tx, Optional.empty(), Optional.of(row)),
                    new SpendingRowChange(
                            SpendingKind.EXPENSE, ChangeOperation.UPDATED, tx, Optional.of(row), Optional.of(row)),
                    new SpendingRowChange(
                            SpendingKind.EXPENSE, ChangeOperation.DELETED, tx, Optional.of(row), Optional.empty()),
                    new SpendingRowChange(
                            SpendingKind.PROPOSAL, ChangeOperation.CREATED, tx, Optional.empty(), Optional.of(row)));
        }

        @Test
        @DisplayName("when a category UPDATED carries a parent and a new name - then renameCategory() gets the "
                + "category id and after.name")
        void whenCategoryUpdatedWithParentAndNewName_thenRenameCategoryReceivesCategoryIdAndAfterName() {
            CategoryRowChange change = RecordedChangeFixtures.categoryUpdated();

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            CategoryRow after = change.after().orElseThrow();
            verify(recordedExpenseStorePort).renameCategory(eq(after.id()), eq(after.name()));
        }

        @Test
        @DisplayName("when a category UPDATED has no parent and a new name - then renameGrouping() gets the "
                + "user id, before and after names")
        void whenCategoryUpdatedWithNoParentAndNewName_thenRenameGroupingReceivesUserIdBeforeNameAndAfterName() {
            CategoryRow before = RecordedChangeFixtures.categoryRow(
                    RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                    Optional.empty(),
                    RecordedChangeFixtures.DEFAULT_GROUPING_NAME);
            CategoryRow after = RecordedChangeFixtures.categoryRow(before.id(), Optional.empty(), "Leisure");
            CategoryRowChange change =
                    new CategoryRowChange(ChangeOperation.UPDATED, Optional.of(before), Optional.of(after));

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verify(recordedExpenseStorePort)
                    .renameGrouping(eq(RecordedChangeFixtures.DEFAULT_USER_ID), eq(before.name()), eq(after.name()));
        }

        @ParameterizedTest
        @MethodSource("categoryChangesStoreIgnores")
        @DisplayName("when a category is moved, created, or deleted - then the store is never touched and the "
                + "outcome is APPLIED")
        void whenCategoryMovedCreatedOrDeleted_thenStoreNeverTouchedAndOutcomeApplied(CategoryRowChange change) {
            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
            verifyNoInteractions(recordedExpenseStorePort);
        }

        private static Stream<CategoryRowChange> categoryChangesStoreIgnores() {
            CategoryRow before = RecordedChangeFixtures.categoryRow();
            CategoryRow moved = RecordedChangeFixtures.categoryRow(before.id(), Optional.of(99L), before.name());
            CategoryRowChange categoryMoved =
                    new CategoryRowChange(ChangeOperation.UPDATED, Optional.of(before), Optional.of(moved));
            return Stream.of(
                    categoryMoved, RecordedChangeFixtures.categoryCreated(), RecordedChangeFixtures.categoryDeleted());
        }

        @Test
        @DisplayName("when the store applies the change - then attempts.clear() receives the delivery id")
        void whenStoreApplies_thenAttemptsClearReceivesDeliveryId() {
            useCase.learn(command(RecordedChangeFixtures.proposalCreated()));

            verify(changeAttemptStorePort).clear(eq(DELIVERY_ID));
        }

        @Test
        @DisplayName("when the store throws MessageStoreUnavailableException 3x - then RETRY_LATER, uncounted, "
                + "WARN per call")
        void whenStoreThrowsUnavailableThreeTimes_thenRetryLaterCountFailureNeverCalledAndOneWarnPerCall() {
            doThrow(new MessageStoreUnavailableException("unreachable"))
                    .when(recordedExpenseStorePort)
                    .recordProposed(any());
            LearnMessageOutcomeCommand command = command(RecordedChangeFixtures.proposalCreated());

            for (int i = 0; i < ENTRY_ATTEMPTS; i++) {
                LearnOutcome outcome = useCase.learn(command);
                assertThat(outcome).isEqualTo(LearnOutcome.RETRY_LATER);
            }

            verify(changeAttemptStorePort, never()).countFailure(any(), any());
            assertThat(loggedWarnLines()).hasSize(ENTRY_ATTEMPTS);
        }

        @Test
        @DisplayName("when the store fails and the attempt store answers 1 then 2 - then RETRY_LATER, counted, "
                + "no ERROR logged")
        void whenStoreThrowsFailedAndAttemptStoreAnswersOneThenTwo_thenRetryLaterCountFailureCalledNoErrorLogged() {
            when(changeAttemptStorePort.countFailure(any(), any())).thenReturn(1, 2);
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .recordProposed(any());
            LearnMessageOutcomeCommand command = command(RecordedChangeFixtures.proposalCreated());

            for (int i = 0; i < 2; i++) {
                LearnOutcome outcome = useCase.learn(command);
                assertThat(outcome).isEqualTo(LearnOutcome.RETRY_LATER);
            }

            verify(changeAttemptStorePort, times(2)).countFailure(eq(DELIVERY_ID), any());
            assertThat(loggedErrorLines()).isEmpty();
        }

        @Test
        @DisplayName("when an expense CREATED fails at entryAttempts - then DROPPED, ERROR names ids, "
                + "abandonAcceptance(), clear() called")
        void whenExpenseCreatedFailsAtEntryAttempts_thenDroppedErrorLoggedAbandonAcceptanceAndClearCalled() {
            when(changeAttemptStorePort.countFailure(any(), any())).thenReturn(ENTRY_ATTEMPTS);
            SpendingRowChange change = RecordedChangeFixtures.expenseCreated();
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .settleExpenseInserted(any(), any());

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.DROPPED);
            assertThat(loggedErrorLines()).hasSize(1);
            SpendingRow after = change.after().orElseThrow();
            assertThat(loggedErrorLines().get(0))
                    .contains(DELIVERY_ID)
                    .contains(change.kind().toString())
                    .contains(change.op().toString())
                    .contains(String.valueOf(after.id()))
                    .doesNotContain(RecordedChangeFixtures.DEFAULT_DESCRIPTION);
            MessageIdentity expectedIdentity = new MessageIdentity(
                    RecordedChangeFixtures.DEFAULT_USER_ID, RecordedChangeFixtures.DEFAULT_MESSAGE_ID);
            verify(recordedExpenseStorePort).abandonAcceptance(eq(expectedIdentity), eq(change.transactionId()));
            verify(changeAttemptStorePort).clear(eq(DELIVERY_ID));
        }

        @ParameterizedTest
        @MethodSource("changesDroppedWithoutAbandon")
        @DisplayName(
                "when a non-expense-CREATED change is dropped - then DROPPED, abandonAcceptance() never " + "called")
        void whenNonExpenseCreatedChangeIsDropped_thenDroppedAndAbandonAcceptanceNeverCalled(RecordedChange change) {
            when(changeAttemptStorePort.countFailure(any(), any())).thenReturn(ENTRY_ATTEMPTS);
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .settleProposalDeleted(any(), any());
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .renameCategory(anyLong(), any());
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .refileExpense(any());
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .recordProposed(any());

            LearnOutcome outcome = useCase.learn(command(change));

            assertThat(outcome).isEqualTo(LearnOutcome.DROPPED);
            verify(recordedExpenseStorePort, never()).abandonAcceptance(any(), any());
        }

        private static Stream<RecordedChange> changesDroppedWithoutAbandon() {
            return Stream.of(
                    RecordedChangeFixtures.proposalDeleted(),
                    RecordedChangeFixtures.categoryUpdated(),
                    RecordedChangeFixtures.expenseUpdated(),
                    RecordedChangeFixtures.proposalCreated());
        }

        @Test
        @DisplayName("when the attempt store throws MessageStoreUnavailableException while counting - then "
                + "RETRY_LATER, nothing propagates")
        void whenAttemptStoreThrowsUnavailableWhileCounting_thenRetryLaterAndNothingPropagates() {
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .recordProposed(any());
            doThrow(new MessageStoreUnavailableException("attempt store unreachable"))
                    .when(changeAttemptStorePort)
                    .countFailure(any(), any());
            LearnMessageOutcomeCommand command = command(RecordedChangeFixtures.proposalCreated());

            AtomicReference<LearnOutcome> outcome = new AtomicReference<>();
            assertThatCode(() -> outcome.set(useCase.learn(command))).doesNotThrowAnyException();

            assertThat(outcome.get()).isEqualTo(LearnOutcome.RETRY_LATER);
        }

        @Test
        @DisplayName("when a drop's abandonAcceptance() throws MessageStoreFailedException - then still "
                + "DROPPED, one WARN logged")
        void whenAbandonAcceptanceThrowsFailedException_thenStillDroppedAndOneWarnLogged() {
            when(changeAttemptStorePort.countFailure(any(), any())).thenReturn(ENTRY_ATTEMPTS);
            SpendingRowChange change = RecordedChangeFixtures.expenseCreated();
            doThrow(new MessageStoreFailedException("failed"))
                    .when(recordedExpenseStorePort)
                    .settleExpenseInserted(any(), any());
            doThrow(new MessageStoreFailedException("abandon failed"))
                    .when(recordedExpenseStorePort)
                    .abandonAcceptance(any(), any());
            LearnMessageOutcomeCommand command = command(change);

            AtomicReference<LearnOutcome> outcome = new AtomicReference<>();
            assertThatCode(() -> outcome.set(useCase.learn(command))).doesNotThrowAnyException();

            assertThat(outcome.get()).isEqualTo(LearnOutcome.DROPPED);
            assertThat(loggedWarnLines()).hasSize(1);
        }
    }
}
