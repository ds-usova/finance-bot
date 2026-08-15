package bot.finance.adapter.cdc;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Starts {@link ChangeStreamReader} when the application context comes up and stops it on shutdown, mirroring
 * {@code TelegramLongPollingSubscriber}'s use of {@link SmartLifecycle} for the same purpose.
 */
@Component
@ConditionalOnProperty(name = "cdc.enabled", havingValue = "true")
public class ChangeStreamLifecycle implements SmartLifecycle {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration START_POLL_INTERVAL = Duration.ofMillis(50);

    private final ChangeStreamReader changeStreamReader;

    private volatile boolean running;

    public ChangeStreamLifecycle(ChangeStreamReader changeStreamReader) {
        this.changeStreamReader = changeStreamReader;
    }

    @Override
    public void start() {
        changeStreamReader.start();
        awaitSlotEstablished();
        running = true;
    }

    /**
     * The reader launches its engine on a background thread and returns immediately, so without this wait the
     * application would already be serving requests while the replication slot is still being created - and a
     * row changed in that window is gone from the log before the slot ever exists to capture it, unlike any
     * later outage. A standby instance never leaves {@code DOWN} here since another holds the slot, so the wait
     * is bounded rather than indefinite; it simply proceeds once the bound elapses.
     */
    private void awaitSlotEstablished() {
        Instant deadline = Instant.now().plus(START_TIMEOUT);
        while (changeStreamReader.state() == ChangeStreamState.DOWN
                && Instant.now().isBefore(deadline)) {
            sleepQuietly(START_POLL_INTERVAL);
        }
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        changeStreamReader.stop(STOP_TIMEOUT);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
