package bot.finance.adapter.cdc;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Owns the embedded Debezium engine's lifecycle: builds it against {@link CdcProperties}, runs it on the
 * shared {@link Executor}, offers each captured event to {@link ChangeEventPublisher}, and commits the log
 * position only once an event is published.
 */
@Component
public class ChangeStreamReader {

    /** What {@code pgoutput} answers when another connection already holds the slot - the retryable failure. */
    private static final String SLOT_HELD_ELSEWHERE_MARKER = "is active for PID";

    private static final Duration START_RETRY_BACKOFF = Duration.ofSeconds(2);
    private static final Duration PUBLISH_RETRY_BACKOFF = Duration.ofSeconds(1);
    private static final Duration CLOSE_RETRY_BACKOFF = Duration.ofMillis(200);

    /** What the engine answers when {@code close()} is called while its tasks are still starting up. */
    private static final String TASKS_STARTING_MARKER = "starting";

    /**
     * The legacy {@code EmbeddedEngine} that {@link DebeziumEngine#create(Class)} builds by default calls a
     * {@code SourceTask.commitRecord} overload Kafka Connect removed; the async engine is the one that still
     * works against this module's {@code connect-api} version.
     */
    private static final String ASYNC_ENGINE_BUILDER_FACTORY =
            "io.debezium.embedded.async.ConvertingAsyncEngineBuilderFactory";

    private final CdcProperties properties;
    private final io.debezium.config.Configuration engineConfiguration;
    private final ChangeEventPublisher publisher;
    private final ChangeStreamMeters meters;
    private final Executor changeStreamExecutor;
    private final Logger log;

    private final Object lifecycleLock = new Object();
    private DebeziumEngine<ChangeEvent<String, String>> engine;
    private CountDownLatch completionLatch;
    private volatile boolean stopRequested;
    private volatile ChangeStreamState currentState = ChangeStreamState.DOWN;

    public ChangeStreamReader(
            CdcProperties properties,
            io.debezium.config.Configuration engineConfiguration,
            ChangeEventPublisher publisher,
            ChangeStreamMeters meters,
            Executor changeStreamExecutor,
            LoggerFactory loggerFactory) {
        this.properties = properties;
        this.engineConfiguration = engineConfiguration;
        this.publisher = publisher;
        this.meters = meters;
        this.changeStreamExecutor = changeStreamExecutor;
        this.log = loggerFactory.getLogger(ChangeStreamReader.class);
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (engine != null) {
                return;
            }
            stopRequested = false;
        }
        launchEngine();
    }

    public boolean stop(Duration timeout) {
        DebeziumEngine<ChangeEvent<String, String>> currentEngine;
        CountDownLatch latch;
        synchronized (lifecycleLock) {
            stopRequested = true;
            currentEngine = engine;
            latch = completionLatch;
        }

        if (currentEngine == null) {
            setState(ChangeStreamState.DOWN);
            return true;
        }

        Instant deadline = Instant.now().plus(timeout);
        closeEngine(currentEngine, deadline);

        boolean finished;
        try {
            finished = latch.await(millisUntil(deadline), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finished = false;
        }

        setState(ChangeStreamState.DOWN);
        return finished;
    }

    /**
     * Closing the engine while its tasks are still starting is refused outright ("wait for the tasks to be fully
     * started"); a short retry loop rides out that window instead of failing the whole stop. Any other refusal
     * (already stopping, already stopped) means someone else is already bringing it down, so nothing further is
     * done here besides waiting for its completion latch.
     */
    private void closeEngine(DebeziumEngine<ChangeEvent<String, String>> engineToClose, Instant deadline) {
        while (true) {
            try {
                engineToClose.close();
                return;
            } catch (IllegalStateException e) {
                boolean stillStarting = e.getMessage() != null && e.getMessage().contains(TASKS_STARTING_MARKER);
                if (!stillStarting || Instant.now().isAfter(deadline)) {
                    return;
                }
                sleepQuietly(CLOSE_RETRY_BACKOFF);
            } catch (IOException e) {
                log.error("Failed to close the change stream engine for slot {}", properties.slotName(), e);
                return;
            }
        }
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long millisUntil(Instant deadline) {
        return Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
    }

    public ChangeStreamState state() {
        return currentState;
    }

    private void launchEngine() {
        CountDownLatch latch = new CountDownLatch(1);
        DebeziumEngine<ChangeEvent<String, String>> newEngine = DebeziumEngine.create(
                        Json.class, Json.class, Json.class, ASYNC_ENGINE_BUILDER_FACTORY)
                .using(engineConfiguration.asProperties())
                .notifying(this::handleBatch)
                .using(new StreamingCallback())
                .using((success, message, error) -> onEngineCompletion(success, message, error, latch))
                .build();

        synchronized (lifecycleLock) {
            this.engine = newEngine;
            this.completionLatch = latch;
        }
        changeStreamExecutor.execute(newEngine);
    }

    private void handleBatch(
            List<ChangeEvent<String, String>> records,
            DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> committer)
            throws InterruptedException {
        for (ChangeEvent<String, String> record : records) {
            if (!isHeartbeat(record)) {
                publishWithRetry(record);
            }
            committer.markProcessed(record);
        }
        committer.markBatchFinished();
    }

    /**
     * A refused write leaves the position uncommitted and marks the stream {@code DOWN} for as long as the
     * refusal lasts, so the health component reflects the outage rather than the engine still holding the slot.
     */
    private void publishWithRetry(ChangeEvent<String, String> record) throws InterruptedException {
        if (publisher.publish(record)) {
            return;
        }

        log.debug("Change stream publish refused for slot {}, entering backoff", properties.slotName());
        setState(ChangeStreamState.DOWN);
        while (!publisher.publish(record)) {
            Thread.sleep(PUBLISH_RETRY_BACKOFF.toMillis());
        }
        log.debug("Change stream publish recovered for slot {}", properties.slotName());
        setState(ChangeStreamState.STREAMING);
    }

    /**
     * The heartbeat action query moves the slot forward without ever naming a captured table, but it still
     * flows through this same consumer as its own record, on Debezium's {@code __debezium-heartbeat} topic
     * rather than one of the captured tables' - marked processed to advance the position, never published.
     */
    private boolean isHeartbeat(ChangeEvent<String, String> record) {
        return record.destination() != null && record.destination().contains("heartbeat");
    }

    private void onEngineCompletion(boolean success, String message, Throwable error, CountDownLatch latch) {
        synchronized (lifecycleLock) {
            engine = null;
            completionLatch = null;
        }
        latch.countDown();

        if (stopRequested || success) {
            return;
        }

        if (isSlotHeldElsewhere(message, error)) {
            log.debug("Replication slot {} is held by another connection, retrying", properties.slotName());
            setState(ChangeStreamState.STANDBY);
            scheduleRetry();
        } else {
            log.error("Change stream engine for slot {} failed and will not retry", properties.slotName(), error);
            setState(ChangeStreamState.DOWN);
        }
    }

    private void scheduleRetry() {
        changeStreamExecutor.execute(() -> {
            try {
                Thread.sleep(START_RETRY_BACKOFF.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!stopRequested) {
                launchEngine();
            }
        });
    }

    private boolean isSlotHeldElsewhere(String message, Throwable error) {
        if (message != null && message.contains(SLOT_HELD_ELSEWHERE_MARKER)) {
            return true;
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(SLOT_HELD_ELSEWHERE_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private void setState(ChangeStreamState newState) {
        currentState = newState;
        meters.setState(newState);
    }

    /** The task starting is the earliest point the connector has actually acquired the slot and begun streaming. */
    private class StreamingCallback implements DebeziumEngine.ConnectorCallback {

        @Override
        public void taskStarted() {
            setState(ChangeStreamState.STREAMING);
        }
    }
}
