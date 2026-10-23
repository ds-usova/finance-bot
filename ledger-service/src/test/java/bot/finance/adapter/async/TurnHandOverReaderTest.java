package bot.finance.adapter.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.port.HandOverIncomingTurnPort;
import bot.finance.application.port.HandleWrittenMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SweepIncomingTurnsPort;
import bot.finance.application.port.SweepWrittenMessagesPort;
import bot.finance.domain.exception.PersistenceFailedException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class TurnHandOverReaderTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);

    private HandOverIncomingTurnPort handOverIncomingTurnPort;
    private HandleWrittenMessagePort handleWrittenMessagePort;
    private SweepIncomingTurnsPort sweepIncomingTurnsPort;
    private SweepWrittenMessagesPort sweepWrittenMessagesPort;
    private LoggerFactory loggerFactory;

    @BeforeEach
    void setUp() {
        handOverIncomingTurnPort = mock(HandOverIncomingTurnPort.class);
        handleWrittenMessagePort = mock(HandleWrittenMessagePort.class);
        sweepIncomingTurnsPort = mock(SweepIncomingTurnsPort.class);
        sweepWrittenMessagesPort = mock(SweepWrittenMessagesPort.class);
        loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(TurnHandOverReader.class)).thenReturn(mock(Logger.class));
    }

    private TurnHandOverReader readerOn(ScheduledExecutorService executor) {
        return new TurnHandOverReader(
                executor,
                handOverIncomingTurnPort,
                handleWrittenMessagePort,
                sweepIncomingTurnsPort,
                sweepWrittenMessagesPort,
                new WrittenMessageReaderProperties(POLL_INTERVAL, Duration.ofMinutes(5), Duration.ofMinutes(10)),
                loggerFactory);
    }

    private static Runnable scheduledPassOf(ScheduledExecutorService executor, TurnHandOverReader reader) {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(executor).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
        reader.start();
        ArgumentCaptor<Runnable> pass = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).scheduleWithFixedDelay(pass.capture(), anyLong(), anyLong(), any());
        return pass.getValue();
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("when the reader is started - then one pass is scheduled every poll interval from the start")
        void whenReaderIsStarted_thenOnePassIsScheduledEveryPollIntervalFromTheStart() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            doReturn(mock(ScheduledFuture.class))
                    .when(executor)
                    .scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
            TurnHandOverReader reader = readerOn(executor);

            reader.start();

            ArgumentCaptor<Long> initialDelay = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<TimeUnit> unit = ArgumentCaptor.forClass(TimeUnit.class);
            verify(executor, times(1))
                    .scheduleWithFixedDelay(any(), initialDelay.capture(), delay.capture(), unit.capture());
            assertThat(unit.getValue().toMillis(initialDelay.getValue())).isZero();
            assertThat(unit.getValue().toMillis(delay.getValue())).isEqualTo(POLL_INTERVAL.toMillis());
            assertThat(reader.isRunning()).isTrue();
        }

        @Test
        @DisplayName("when the scheduled pass runs - then it sweeps once and reads until nothing waits")
        void whenScheduledPassRuns_thenItSweepsOnceAndReadsUntilNothingWaits() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            when(handOverIncomingTurnPort.handOverNext()).thenReturn(true, false);
            when(handleWrittenMessagePort.handleNext()).thenReturn(true, true, false);
            Runnable pass = scheduledPassOf(executor, readerOn(executor));

            pass.run();

            InOrder order = inOrder(
                    sweepIncomingTurnsPort,
                    sweepWrittenMessagesPort,
                    handOverIncomingTurnPort,
                    handleWrittenMessagePort);
            order.verify(sweepIncomingTurnsPort).sweep();
            order.verify(sweepWrittenMessagesPort).sweep();
            order.verify(handOverIncomingTurnPort, times(2)).handOverNext();
            order.verify(handleWrittenMessagePort, times(3)).handleNext();
            verify(sweepIncomingTurnsPort, times(1)).sweep();
            verify(sweepWrittenMessagesPort, times(1)).sweep();
        }

        @Test
        @DisplayName("when the Telegram sweep fails - then nothing propagates and the other sweep and both looks run")
        void whenTelegramSweepFails_thenNothingPropagatesAndOtherSweepAndBothLooksRun() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            doThrow(new PersistenceFailedException("sweep failed", new RuntimeException()))
                    .when(sweepIncomingTurnsPort)
                    .sweep();
            Runnable pass = scheduledPassOf(executor, readerOn(executor));

            assertThatCode(pass::run).doesNotThrowAnyException();

            verify(sweepWrittenMessagesPort).sweep();
            verify(handOverIncomingTurnPort).handOverNext();
            verify(handleWrittenMessagePort).handleNext();
        }

        @Test
        @DisplayName("when the Telegram hand-over fails - then nothing propagates and the written look still runs")
        void whenTelegramHandOverFails_thenNothingPropagatesAndWrittenLookStillRuns() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            when(handOverIncomingTurnPort.handOverNext())
                    .thenThrow(new PersistenceFailedException("hand-over failed", new RuntimeException()));
            Runnable pass = scheduledPassOf(executor, readerOn(executor));

            assertThatCode(pass::run).doesNotThrowAnyException();

            verify(handOverIncomingTurnPort).handOverNext();
            verify(handleWrittenMessagePort).handleNext();
        }

        @Test
        @DisplayName("when the Telegram hand-over answers true, true, then false - then it is called three times")
        void whenTelegramHandOverAnswersTrueTrueThenFalse_thenItIsCalledThreeTimes() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            when(handOverIncomingTurnPort.handOverNext()).thenReturn(true, true, false);
            Runnable pass = scheduledPassOf(executor, readerOn(executor));

            pass.run();

            verify(handOverIncomingTurnPort, times(3)).handOverNext();
        }

        @Test
        @DisplayName("when the sweep fails - then nothing propagates and the pass still reads")
        void whenSweepFails_thenNothingPropagatesAndPassStillReads() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            doThrow(new PersistenceFailedException("sweep failed", new RuntimeException()))
                    .when(sweepWrittenMessagesPort)
                    .sweep();
            Runnable pass = scheduledPassOf(executor, readerOn(executor));

            assertThatCode(pass::run).doesNotThrowAnyException();

            verify(handleWrittenMessagePort).handleNext();
        }

        @Test
        @DisplayName("when reading fails on one pass - then nothing propagates and the next pass reads again")
        void whenReadingFailsOnOnePass_thenNothingPropagatesAndNextPassReadsAgain() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            when(handleWrittenMessagePort.handleNext())
                    .thenThrow(new PersistenceFailedException("read failed", new RuntimeException()))
                    .thenReturn(false);
            Runnable pass = scheduledPassOf(executor, readerOn(executor));

            assertThatCode(pass::run).doesNotThrowAnyException();
            assertThatCode(pass::run).doesNotThrowAnyException();

            verify(handleWrittenMessagePort, times(2)).handleNext();
        }
    }

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        @DisplayName("when a started reader is stopped - then the scheduled pass is cancelled and it is not running")
        void whenStartedReaderIsStopped_thenScheduledPassIsCancelledAndItIsNotRunning() {
            ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            doReturn(future).when(executor).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
            TurnHandOverReader reader = readerOn(executor);
            reader.start();

            reader.stop();

            verify(future).cancel(anyBoolean());
            assertThat(reader.isRunning()).isFalse();
        }
    }

    @Nested
    @DisplayName("dispatch()")
    class Dispatch {

        // Answers every call handing the executor a Runnable or Callable, whichever method the reader picks.
        private ScheduledExecutorService executorHandling(AtomicInteger handed, boolean refuse) {
            return mock(ScheduledExecutorService.class, invocation -> {
                for (Object argument : invocation.getArguments()) {
                    if (argument instanceof Runnable task) {
                        handed.incrementAndGet();
                        if (refuse) {
                            throw new RejectedExecutionException("pool has no room");
                        }
                        task.run();
                    } else if (argument instanceof Callable<?> task) {
                        handed.incrementAndGet();
                        if (refuse) {
                            throw new RejectedExecutionException("pool has no room");
                        }
                        task.call();
                    }
                }
                return null;
            });
        }

        @Test
        @DisplayName("when the executor runs what it is handed at once - then one pass sweeps and then reads")
        void whenExecutorRunsWhatItIsHandedAtOnce_thenOnePassSweepsAndThenReads() {
            AtomicInteger handed = new AtomicInteger();
            TurnHandOverReader reader = readerOn(executorHandling(handed, false));

            reader.dispatch();

            assertThat(handed).hasValue(1);
            InOrder order = inOrder(
                    sweepIncomingTurnsPort,
                    sweepWrittenMessagesPort,
                    handOverIncomingTurnPort,
                    handleWrittenMessagePort);
            order.verify(sweepIncomingTurnsPort).sweep();
            order.verify(sweepWrittenMessagesPort).sweep();
            order.verify(handOverIncomingTurnPort).handOverNext();
            order.verify(handleWrittenMessagePort).handleNext();
        }

        @Test
        @DisplayName("when the executor refuses the work - then nothing propagates and neither port is called")
        void whenExecutorRefusesTheWork_thenNothingPropagatesAndNeitherPortIsCalled() {
            AtomicInteger handed = new AtomicInteger();
            TurnHandOverReader reader = readerOn(executorHandling(handed, true));

            assertThatCode(reader::dispatch).doesNotThrowAnyException();

            assertThat(handed).hasValue(1);
            verifyNoInteractions(
                    sweepIncomingTurnsPort,
                    sweepWrittenMessagesPort,
                    handOverIncomingTurnPort,
                    handleWrittenMessagePort);
        }
    }
}
