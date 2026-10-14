package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.config.ExtractionProperties;
import bot.finance.ai.application.dto.TurnStart;
import bot.finance.ai.application.port.TurnRunnerPort;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import io.grpc.Context;
import jakarta.annotation.PreDestroy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class GrpcTurnRunnerAdapter implements TurnRunnerPort {

    private final ExtractionProperties properties;
    private final ExecutorService turns = Executors.newCachedThreadPool(daemonThreads("turn-runner-"));
    private final ScheduledExecutorService timeouts =
            Executors.newSingleThreadScheduledExecutor(daemonThreads("turn-timeout-"));
    private final Set<MessageIdentity> inFlight = new HashSet<>();
    private boolean closed;

    public GrpcTurnRunnerAdapter(ExtractionProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized TurnStart start(MessageIdentity turnKey, Supplier<TurnEnd> turn, Consumer<TurnEnd> end) {
        if (closed) {
            return TurnStart.BUSY;
        }
        if (inFlight.contains(turnKey)) {
            return TurnStart.ALREADY_RUNNING;
        }
        if (inFlight.size() >= properties.maxTurns()) {
            return TurnStart.BUSY;
        }

        inFlight.add(turnKey);
        Context forked = Context.current().fork();
        turns.execute(forked.wrap(() -> runTurn(turnKey, turn, end)));
        return TurnStart.STARTED;
    }

    @PreDestroy
    public void close() {
        turns.shutdown();
        try {
            awaitInFlightEnds();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            timeouts.shutdownNow();
        }
    }

    private synchronized void awaitInFlightEnds() throws InterruptedException {
        closed = true;
        long deadline = System.nanoTime() + 2 * properties.turnTimeout().toNanos();
        long remaining = deadline - System.nanoTime();
        while (!inFlight.isEmpty() && remaining > 0) {
            TimeUnit.NANOSECONDS.timedWait(this, remaining);
            remaining = deadline - System.nanoTime();
        }
    }

    private void runTurn(MessageIdentity turnKey, Supplier<TurnEnd> turn, Consumer<TurnEnd> end) {
        try {
            end.accept(runWithinTimeout(turn));
        } finally {
            release(turnKey);
        }
    }

    private TurnEnd runWithinTimeout(Supplier<TurnEnd> turn) {
        Thread turnThread = Thread.currentThread();
        Object guard = new Object();
        boolean[] state = new boolean[2]; // [0] finished, [1] timed out
        ScheduledFuture<?> timeout = timeouts.schedule(
                () -> {
                    synchronized (guard) {
                        if (!state[0]) {
                            state[1] = true;
                            turnThread.interrupt();
                        }
                    }
                },
                properties.turnTimeout().toMillis(),
                TimeUnit.MILLISECONDS);

        TurnEnd answer;
        try {
            answer = turn.get();
        } catch (RuntimeException e) {
            answer = TurnEnd.FAILED;
        }

        synchronized (guard) {
            state[0] = true;
            timeout.cancel(false);
            Thread.interrupted();
            return state[1] ? TurnEnd.UNAVAILABLE : answer;
        }
    }

    private synchronized void release(MessageIdentity turnKey) {
        inFlight.remove(turnKey);
        notifyAll();
    }

    private static ThreadFactory daemonThreads(String prefix) {
        ThreadFactory delegate = Executors.defaultThreadFactory();
        return runnable -> {
            Thread thread = delegate.newThread(runnable);
            thread.setName(prefix + thread.getName());
            thread.setDaemon(true);
            return thread;
        };
    }
}
