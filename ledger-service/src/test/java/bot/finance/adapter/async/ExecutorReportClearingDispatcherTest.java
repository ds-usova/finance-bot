package bot.finance.adapter.async;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import bot.finance.application.dto.ClearEmptiedReportsCommand;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.IncomingMessageId;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExecutorReportClearingDispatcherTest {

    private static final long USER_ID = 1L;

    private ClearEmptiedReportsCommand command() {
        return new ClearEmptiedReportsCommand(USER_ID, List.of(IncomingMessageId.of("777:123")));
    }

    @Nested
    @DisplayName("dispatch(ClearEmptiedReportsCommand)")
    class Dispatch {

        @Test
        @DisplayName("when the executor runs what it is handed - then the clearing port receives exactly the "
                + "command it was given")
        void whenExecutorRunsWhatItIsHanded_thenClearingPortReceivesExactlyTheCommandItWasGiven() {
            ClearEmptiedReportsPort clearEmptiedReportsPort = mock(ClearEmptiedReportsPort.class);
            Executor synchronousExecutor = Runnable::run;
            ExecutorReportClearingDispatcher dispatcher =
                    new ExecutorReportClearingDispatcher(synchronousExecutor, clearEmptiedReportsPort);
            ClearEmptiedReportsCommand command = command();

            dispatcher.dispatch(command);

            verify(clearEmptiedReportsPort).clear(command);
        }

        @Test
        @DisplayName("when the executor rejects the work - then nothing propagates and the clearing port is untouched")
        void whenExecutorRejectsWork_thenNothingPropagatesAndClearingPortUntouched() {
            ClearEmptiedReportsPort clearEmptiedReportsPort = mock(ClearEmptiedReportsPort.class);
            Executor refusingExecutor = task -> {
                throw new RejectedExecutionException("pool has no room");
            };
            ExecutorReportClearingDispatcher dispatcher =
                    new ExecutorReportClearingDispatcher(refusingExecutor, clearEmptiedReportsPort);

            assertThatCode(() -> dispatcher.dispatch(command())).doesNotThrowAnyException();

            verifyNoInteractions(clearEmptiedReportsPort);
        }

        @Test
        @DisplayName("when the executor runs the work and the clearing port throws - then nothing propagates to "
                + "the caller")
        void whenExecutorRunsWorkAndClearingPortThrows_thenNothingPropagatesToTheCaller() {
            ClearEmptiedReportsPort clearEmptiedReportsPort = mock(ClearEmptiedReportsPort.class);
            ClearEmptiedReportsCommand command = command();
            doThrow(new PersistenceFailedException("clearing failed", new RuntimeException()))
                    .when(clearEmptiedReportsPort)
                    .clear(command);
            Executor synchronousExecutor = Runnable::run;
            ExecutorReportClearingDispatcher dispatcher =
                    new ExecutorReportClearingDispatcher(synchronousExecutor, clearEmptiedReportsPort);

            assertThatCode(() -> dispatcher.dispatch(command)).doesNotThrowAnyException();
        }
    }
}
