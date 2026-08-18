package bot.finance.ai.application.usecase;

import static bot.finance.ai.common.fixtures.SpendingFactFixtures.position;
import static bot.finance.ai.common.fixtures.SpendingFactFixtures.spendingRow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.common.MockedLoggerUtils;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LearnMessageOutcomeUseCaseTest {

    private static final int ENTRY_ATTEMPTS = 3;
    private static final String DELIVERY_ID = "delivery-1";
    private static final StreamPosition POSITION = position();
    private static final RecordedStatus STATUS = RecordedStatus.ACCEPTED;

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

        @Test
        @DisplayName("when the entry carries a message id - then the store applies it, attempts are cleared "
                + "and the outcome is APPLIED")
        void whenEntryCarriesMessageId_thenStoreAppliesEntryStatusPositionAttemptsClearedAndOutcomeApplied() {
            SpendingRow entry = spendingRow();
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, entry);

            LearnOutcome outcome = useCase.learn(command);

            verify(recordedExpenseStorePort).apply(entry, STATUS, POSITION);
            verify(changeAttemptStorePort).clear(DELIVERY_ID);
            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
        }

        @Test
        @DisplayName("when the entry carries no message id - then the store is never touched and the outcome "
                + "is APPLIED")
        void whenEntryCarriesNoMessageId_thenStoreNeverTouchedAndOutcomeApplied() {
            SpendingRow entry = spendingRow(9002L, Optional.empty());
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, entry);

            LearnOutcome outcome = useCase.learn(command);

            verifyNoInteractions(recordedExpenseStorePort);
            assertThat(outcome).isEqualTo(LearnOutcome.APPLIED);
        }

        @Test
        @DisplayName("when the store throws MessageStoreUnavailableException three times - then RETRY_LATER, "
                + "uncounted, one WARN each")
        void whenStoreThrowsUnavailableThreeTimes_thenEveryOutcomeRetryLaterNoFailureCountedAndOneWarnPerCall() {
            SpendingRow entry = spendingRow();
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, entry);
            doThrow(new MessageStoreUnavailableException("store unreachable"))
                    .when(recordedExpenseStorePort)
                    .apply(any(), any(), any());

            for (int i = 0; i < 3; i++) {
                assertThat(useCase.learn(command)).isEqualTo(LearnOutcome.RETRY_LATER);
            }

            verify(changeAttemptStorePort, never()).countFailure(any(), any());
            List<String> warnLines = MockedLoggerUtils.warnLines(log);
            assertThat(warnLines).hasSize(3);
            assertThat(warnLines).allMatch(line -> line.contains(DELIVERY_ID));
        }

        @Test
        @DisplayName(
                "when the store fails below entryAttempts - then RETRY_LATER, failure counted, no ERROR " + "logged")
        void whenStoreFailsAndAttemptsBelowEntryAttempts_thenRetryLaterFailureCountedAndNoErrorLogged() {
            SpendingRow entry = spendingRow();
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, entry);
            doThrow(new MessageStoreFailedException("store failed"))
                    .when(recordedExpenseStorePort)
                    .apply(any(), any(), any());
            when(changeAttemptStorePort.countFailure(eq(DELIVERY_ID), any())).thenReturn(ENTRY_ATTEMPTS - 1);

            LearnOutcome outcome = useCase.learn(command);

            assertThat(outcome).isEqualTo(LearnOutcome.RETRY_LATER);
            verify(changeAttemptStorePort).countFailure(DELIVERY_ID, "store failed");
            assertThat(MockedLoggerUtils.linesAt(log, "error")).isEmpty();
        }

        @Test
        @DisplayName("when the store fails at entryAttempts - then DROPPED, ERROR logged, and attempts are cleared")
        void whenStoreFailsAtEntryAttempts_thenDroppedErrorLoggedAttemptsClearedAndNothingElseWritten() {
            SpendingRow entry = spendingRow();
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, entry);
            doThrow(new MessageStoreFailedException("store failed"))
                    .when(recordedExpenseStorePort)
                    .apply(any(), any(), any());
            when(changeAttemptStorePort.countFailure(eq(DELIVERY_ID), any())).thenReturn(ENTRY_ATTEMPTS);

            LearnOutcome outcome = useCase.learn(command);

            assertThat(outcome).isEqualTo(LearnOutcome.DROPPED);
            List<String> errorLines = MockedLoggerUtils.linesAt(log, "error");
            assertThat(errorLines).hasSize(1);
            assertThat(errorLines.get(0))
                    .contains(DELIVERY_ID)
                    .contains(STATUS.toString())
                    .contains(String.valueOf(entry.expenseId()));
            verify(changeAttemptStorePort).clear(DELIVERY_ID);
            verify(recordedExpenseStorePort).apply(entry, STATUS, POSITION);
            verifyNoMoreInteractions(recordedExpenseStorePort);
        }

        @Test
        @DisplayName("when the attempt store throws MessageStoreUnavailableException while counting - then "
                + "RETRY_LATER, nothing propagates")
        void whenAttemptStoreThrowsUnavailableWhileCounting_thenRetryLaterAndNothingPropagates() {
            SpendingRow entry = spendingRow();
            LearnMessageOutcomeCommand command = new LearnMessageOutcomeCommand(DELIVERY_ID, POSITION, STATUS, entry);
            doThrow(new MessageStoreFailedException("store failed"))
                    .when(recordedExpenseStorePort)
                    .apply(any(), any(), any());
            doThrow(new MessageStoreUnavailableException("attempt store unreachable"))
                    .when(changeAttemptStorePort)
                    .countFailure(any(), any());

            LearnOutcome[] outcome = new LearnOutcome[1];
            assertThatCode(() -> outcome[0] = useCase.learn(command)).doesNotThrowAnyException();
            assertThat(outcome[0]).isEqualTo(LearnOutcome.RETRY_LATER);
        }
    }
}
