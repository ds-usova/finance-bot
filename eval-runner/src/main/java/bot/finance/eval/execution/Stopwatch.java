package bot.finance.eval.execution;

import java.time.Duration;

/** A monotonic start point; durations in a record are never taken from the wall clock. */
record Stopwatch(long startNanos) {

    static Stopwatch start() {
        return new Stopwatch(System.nanoTime());
    }

    long elapsedMs() {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
