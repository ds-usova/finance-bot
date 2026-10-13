package bot.finance.eval.execution;

import bot.finance.eval.EvalException;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Waits on the application by asking again until an answer holds or the timeout passes. */
final class Poller {

    private static final Duration INTERVAL = Duration.ofMillis(200);

    private Poller() {}

    /** The first answer {@code accepted} holds for. */
    static <T> T await(String stage, Duration timeout, Supplier<T> ask, Predicate<T> accepted, String timeoutMessage) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            T answer = ask.get();
            if (accepted.test(answer)) {
                return answer;
            }
            if (System.nanoTime() > deadline) {
                throw new EvalException(stage, timeoutMessage + " within " + timeout.toSeconds() + "s");
            }
            sleep(INTERVAL);
        }
    }

    static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvalException("runner", "interrupted");
        }
    }
}
