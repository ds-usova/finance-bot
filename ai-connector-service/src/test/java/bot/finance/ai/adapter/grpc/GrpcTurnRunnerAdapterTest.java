package bot.finance.ai.adapter.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.ai.adapter.config.ExtractionProperties;
import bot.finance.ai.application.dto.TurnStart;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import io.grpc.Context;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GrpcTurnRunnerAdapterTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final String TOKEN = "Bearer caller-token";

    private static final MessageIdentity KEY = new MessageIdentity(42, "m-1");
    private static final MessageIdentity OTHER_KEY = new MessageIdentity(42, "m-2");
    private static final MessageIdentity THIRD_KEY = new MessageIdentity(42, "m-3");

    private final List<ScriptedTurn> turns = new CopyOnWriteArrayList<>();
    private final List<GrpcTurnRunnerAdapter> runners = new CopyOnWriteArrayList<>();
    private final List<RecordingEnd> ends = new CopyOnWriteArrayList<>();

    @AfterEach
    void releaseEverything() {
        turns.forEach(ScriptedTurn::release);
        ends.forEach(RecordingEnd::open);
        runners.forEach(GrpcTurnRunnerAdapter::close);
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("when a slot is free and no turn runs under the key - then it starts the turn on another thread")
        void whenSlotFreeAndKeyFree_thenStartsTurnOnAnotherThread() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn turn = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = end();

            TurnStart answer = runner.start(KEY, turn, end);

            assertThat(answer).isEqualTo(TurnStart.STARTED);
            assertThat(end.awaitEnded()).isTrue();
            assertThat(turn.runThread.get()).isNotNull().isNotSameAs(Thread.currentThread());
            assertThat(end.received).containsExactly(TurnEnd.ACTED_ON);
        }

        @Test
        @DisplayName(
                "when the caller's context is cancelled once start returns - then the turn runs on and reads the token")
        void whenCallerContextCancelledAfterStart_thenTurnRunsToItsEndAndReadsToken() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn turn = blockingTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = end();
            Context.CancellableContext caller = Context.current().withCancellation();
            AtomicReference<TurnStart> answer = new AtomicReference<>();

            caller.withValue(CallerTokenContext.CALLER_TOKEN, TOKEN)
                    .run(() -> answer.set(runner.start(KEY, turn, end)));
            caller.cancel(null);
            turn.release();

            assertThat(answer.get()).isEqualTo(TurnStart.STARTED);
            assertThat(end.awaitEnded()).isTrue();
            assertThat(turn.tokenAtEnd.get()).isEqualTo(TOKEN);
            assertThat(turn.cancelledAtEnd.get()).isFalse();
            assertThat(end.received).containsExactly(TurnEnd.ACTED_ON);
        }

        @Test
        @DisplayName("when the caller's deadline passes while the turn runs - then the turn runs to its end")
        void whenCallerDeadlinePassesWhileTurnRuns_thenTurnRunsToItsEnd() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn turn = blockingTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = end();
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            Context.CancellableContext caller =
                    Context.current().withDeadlineAfter(100, TimeUnit.MILLISECONDS, scheduler);
            AtomicReference<TurnStart> answer = new AtomicReference<>();
            try {
                caller.run(() -> answer.set(runner.start(KEY, turn, end)));
                await().atMost(WAIT).until(caller::isCancelled);
                turn.release();

                assertThat(answer.get()).isEqualTo(TurnStart.STARTED);
                assertThat(end.awaitEnded()).isTrue();
                assertThat(turn.cancelledAtEnd.get()).isFalse();
                assertThat(turn.deadlineAtEnd.get()).isFalse();
                assertThat(end.received).containsExactly(TurnEnd.ACTED_ON);
            } finally {
                caller.cancel(null);
                scheduler.shutdownNow();
            }
        }

        @Test
        @DisplayName(
                "when a turn runs under the key - then a second turn under it answers ALREADY_RUNNING and never runs")
        void whenTurnRunsUnderKey_thenSecondTurnAnswersAlreadyRunningAndNeverRuns() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn first = blockingTurn(TurnEnd.ACTED_ON);
            ScriptedTurn second = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd firstEnd = end();

            assertThat(runner.start(KEY, first, firstEnd)).isEqualTo(TurnStart.STARTED);
            assertThat(first.awaitRunning()).isTrue();
            TurnStart answer = runner.start(KEY, second, end());
            first.release();
            assertThat(firstEnd.awaitEnded()).isTrue();

            assertThat(answer).isEqualTo(TurnStart.ALREADY_RUNNING);
            assertThat(second.ran.get()).isFalse();
        }

        @Test
        @DisplayName(
                "when a turn's end has not yet returned - then a second turn under its key answers ALREADY_RUNNING")
        void whenEndHasNotReturned_thenSecondTurnUnderKeyAnswersAlreadyRunning() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            RecordingEnd firstEnd = gatedEnd();

            assertThat(runner.start(KEY, instantTurn(TurnEnd.ACTED_ON), firstEnd))
                    .isEqualTo(TurnStart.STARTED);
            assertThat(firstEnd.awaitEnded()).isTrue();
            TurnStart answer = runner.start(KEY, instantTurn(TurnEnd.ACTED_ON), end());
            firstEnd.open();

            assertThat(answer).isEqualTo(TurnStart.ALREADY_RUNNING);
        }

        @Test
        @DisplayName("when a turn under the key ended and its end returned - then a turn under it starts again")
        void whenTurnUnderKeyEndedAndEndReturned_thenTurnUnderKeyStartsAgain() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            RecordingEnd firstEnd = end();
            ScriptedTurn second = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd secondEnd = end();

            assertThat(runner.start(KEY, instantTurn(TurnEnd.ACTED_ON), firstEnd))
                    .isEqualTo(TurnStart.STARTED);
            assertThat(firstEnd.awaitEnded()).isTrue();

            await().atMost(WAIT).until(() -> runner.start(KEY, second, secondEnd) == TurnStart.STARTED);
            assertThat(secondEnd.awaitEnded()).isTrue();
            assertThat(second.ran.get()).isTrue();
        }

        @Test
        @DisplayName("when as many turns run as the max allows - then one more answers BUSY and never runs")
        void whenMaxTurnsRun_thenOneMoreAnswersBusyAndNeverRuns() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn extra = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd firstEnd = end();
            RecordingEnd secondEnd = end();
            ScriptedTurn first = blockingTurn(TurnEnd.ACTED_ON);
            ScriptedTurn second = blockingTurn(TurnEnd.ACTED_ON);

            assertThat(runner.start(KEY, first, firstEnd)).isEqualTo(TurnStart.STARTED);
            assertThat(runner.start(OTHER_KEY, second, secondEnd)).isEqualTo(TurnStart.STARTED);
            TurnStart answer = runner.start(THIRD_KEY, extra, end());
            first.release();
            second.release();
            assertThat(firstEnd.awaitEnded()).isTrue();
            assertThat(secondEnd.awaitEnded()).isTrue();

            assertThat(answer).isEqualTo(TurnStart.BUSY);
            assertThat(extra.ran.get()).isFalse();
        }

        @Test
        @DisplayName(
                "when the max runs and one runs under the key - then a turn under that key answers ALREADY_RUNNING")
        void whenMaxTurnsRunAndOneUnderKey_thenTurnUnderThatKeyAnswersAlreadyRunning() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn first = blockingTurn(TurnEnd.ACTED_ON);
            ScriptedTurn second = blockingTurn(TurnEnd.ACTED_ON);
            ScriptedTurn again = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd firstEnd = end();
            RecordingEnd secondEnd = end();

            assertThat(runner.start(KEY, first, firstEnd)).isEqualTo(TurnStart.STARTED);
            assertThat(runner.start(OTHER_KEY, second, secondEnd)).isEqualTo(TurnStart.STARTED);
            TurnStart answer = runner.start(KEY, again, end());
            first.release();
            second.release();
            assertThat(firstEnd.awaitEnded()).isTrue();
            assertThat(secondEnd.awaitEnded()).isTrue();

            assertThat(answer).isEqualTo(TurnStart.ALREADY_RUNNING);
            assertThat(again.ran.get()).isFalse();
        }

        @Test
        @DisplayName("when the max runs and one of them ends - then one more turn starts")
        void whenMaxTurnsRunAndOneEnds_thenOneMoreTurnStarts() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn first = blockingTurn(TurnEnd.ACTED_ON);
            ScriptedTurn second = blockingTurn(TurnEnd.ACTED_ON);
            ScriptedTurn extra = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd firstEnd = end();
            RecordingEnd extraEnd = end();

            assertThat(runner.start(KEY, first, firstEnd)).isEqualTo(TurnStart.STARTED);
            assertThat(runner.start(OTHER_KEY, second, end())).isEqualTo(TurnStart.STARTED);
            first.release();
            assertThat(firstEnd.awaitEnded()).isTrue();

            await().atMost(WAIT).until(() -> runner.start(THIRD_KEY, extra, extraEnd) == TurnStart.STARTED);
            assertThat(extraEnd.awaitEnded()).isTrue();
            assertThat(extra.ran.get()).isTrue();
        }

        @Test
        @DisplayName(
                "when a turn is still running at the turn timeout - then it is interrupted and its end gets UNAVAILABLE")
        void whenTurnRunsPastTimeout_thenInterruptedAndEndGetsUnavailable() {
            GrpcTurnRunnerAdapter runner = runner(2, SHORT_TIMEOUT);
            ScriptedTurn turn = blockingTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = end();

            CallerTokenTestSupport.withCallerToken(TOKEN, () -> {
                assertThat(runner.start(KEY, turn, end)).isEqualTo(TurnStart.STARTED);
            });

            assertThat(end.awaitEnded()).isTrue();
            assertThat(turn.interrupted.get()).isTrue();
            assertThat(end.received).containsExactly(TurnEnd.UNAVAILABLE);
            assertThat(end.interruptedAtEnd.get()).isFalse();
            assertThat(end.tokenAtEnd.get()).isEqualTo(TOKEN);
        }

        @Test
        @DisplayName("when the runner is closed - then a turn answers BUSY and never runs")
        void whenRunnerIsClosed_thenTurnAnswersBusyAndNeverRuns() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn turn = instantTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = end();
            runner.close();

            TurnStart answer = runner.start(KEY, turn, end);

            assertThat(answer).isEqualTo(TurnStart.BUSY);
            assertThat(end.awaitEnded(Duration.ofMillis(300))).isFalse();
            assertThat(turn.ran.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("close()")
    class Close {

        @Test
        @DisplayName("when a turn runs inside the turn timeout - then it returns once that turn and its end are done")
        void whenTurnRunsInsideTimeout_thenReturnsOnceTurnAndEndAreDone() {
            GrpcTurnRunnerAdapter runner = runner(2, LONG_TIMEOUT);
            ScriptedTurn turn = blockingTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = gatedEnd();

            assertThat(runner.start(KEY, turn, end)).isEqualTo(TurnStart.STARTED);
            assertThat(turn.awaitRunning()).isTrue();
            CompletableFuture<Void> closing = CompletableFuture.runAsync(runner::close);
            turn.release();
            assertThat(end.awaitEnded()).isTrue();
            await().during(Duration.ofMillis(300)).atMost(WAIT).until(() -> !closing.isDone());
            end.open();

            assertThat(closing).succeedsWithin(WAIT);
            assertThat(end.finished.get()).isTrue();
            assertThat(end.received).containsExactly(TurnEnd.ACTED_ON);
        }

        @Test
        @DisplayName(
                "when a turn runs past the turn timeout - then it returns once the interrupted turn and its end are done")
        void whenTurnRunsPastTimeout_thenReturnsOnceInterruptedTurnAndEndAreDone() {
            GrpcTurnRunnerAdapter runner = runner(2, SHORT_TIMEOUT);
            ScriptedTurn turn = blockingTurn(TurnEnd.ACTED_ON);
            RecordingEnd end = end();

            assertThat(runner.start(KEY, turn, end)).isEqualTo(TurnStart.STARTED);
            CompletableFuture<Void> closing = CompletableFuture.runAsync(runner::close);

            assertThat(closing).succeedsWithin(WAIT);
            assertThat(end.finished.get()).isTrue();
            assertThat(turn.interrupted.get()).isTrue();
            assertThat(end.received).containsExactly(TurnEnd.UNAVAILABLE);
        }
    }

    private GrpcTurnRunnerAdapter runner(int maxTurns, Duration turnTimeout) {
        GrpcTurnRunnerAdapter runner =
                new GrpcTurnRunnerAdapter(new ExtractionProperties("tools", maxTurns, turnTimeout));
        runners.add(runner);
        return runner;
    }

    private ScriptedTurn instantTurn(TurnEnd answer) {
        ScriptedTurn turn = new ScriptedTurn(answer, false);
        turns.add(turn);
        return turn;
    }

    private ScriptedTurn blockingTurn(TurnEnd answer) {
        ScriptedTurn turn = new ScriptedTurn(answer, true);
        turns.add(turn);
        return turn;
    }

    private RecordingEnd end() {
        RecordingEnd end = new RecordingEnd(false);
        ends.add(end);
        return end;
    }

    private RecordingEnd gatedEnd() {
        RecordingEnd end = new RecordingEnd(true);
        ends.add(end);
        return end;
    }

    /** A turn that notes where and under what context it ran, and, when blocking, waits until released. */
    private static final class ScriptedTurn implements Supplier<TurnEnd> {

        final AtomicBoolean ran = new AtomicBoolean();
        final AtomicBoolean interrupted = new AtomicBoolean();
        final AtomicBoolean cancelledAtEnd = new AtomicBoolean();
        final AtomicBoolean deadlineAtEnd = new AtomicBoolean();
        final AtomicReference<Thread> runThread = new AtomicReference<>();
        final AtomicReference<String> tokenAtEnd = new AtomicReference<>();
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private final TurnEnd answer;

        ScriptedTurn(TurnEnd answer, boolean blocking) {
            this.answer = answer;
            if (!blocking) {
                released.countDown();
            }
        }

        @Override
        public TurnEnd get() {
            ran.set(true);
            runThread.set(Thread.currentThread());
            running.countDown();
            try {
                released.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            tokenAtEnd.set(CallerTokenContext.callerToken().orElse(null));
            cancelledAtEnd.set(Context.current().isCancelled());
            deadlineAtEnd.set(Context.current().getDeadline() != null);
            return answer;
        }

        boolean awaitRunning() {
            try {
                return running.await(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        void release() {
            released.countDown();
        }
    }

    /** An end that records what it received and under what thread state, and, when gated, waits to be opened. */
    private static final class RecordingEnd implements Consumer<TurnEnd> {

        final List<TurnEnd> received = new CopyOnWriteArrayList<>();
        final AtomicBoolean interruptedAtEnd = new AtomicBoolean(true);
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicReference<String> tokenAtEnd = new AtomicReference<>();
        private final CountDownLatch ended = new CountDownLatch(1);
        private final CountDownLatch gate = new CountDownLatch(1);

        RecordingEnd(boolean gated) {
            if (!gated) {
                gate.countDown();
            }
        }

        @Override
        public void accept(TurnEnd turnEnd) {
            received.add(turnEnd);
            interruptedAtEnd.set(Thread.currentThread().isInterrupted());
            tokenAtEnd.set(CallerTokenContext.callerToken().orElse(null));
            ended.countDown();
            try {
                gate.await(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finished.set(true);
        }

        boolean awaitEnded() {
            return awaitEnded(WAIT);
        }

        boolean awaitEnded(Duration within) {
            try {
                return ended.await(within.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        void open() {
            gate.countDown();
        }
    }
}
