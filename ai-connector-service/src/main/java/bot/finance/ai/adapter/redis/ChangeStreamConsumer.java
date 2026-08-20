package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.InvalidValueException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class ChangeStreamConsumer implements SmartLifecycle {

    private static final String GROUP = "ai-connector";
    private static final String FALLBACK_CONSUMER_NAME = "ai-connector-unknown-host";
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);
    private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(2);
    private static final Duration NEW_ENTRIES_BLOCK = Duration.ofSeconds(1);
    private static final long CLAIM_BATCH_SIZE = 100L;

    // XGROUP CREATE answers this when the group already exists; Redis has no "create if absent" flag for it.
    private static final String BUSYGROUP_MARKER = "BUSYGROUP";

    private final StringRedisTemplate redisTemplate;
    private final ChangeStreamProperties properties;
    private final ChangeStreamEntryReader reader;
    private final LearnMessageOutcomePort learnMessageOutcomePort;
    private final ExecutorService executorService;
    private final Logger log;

    private volatile Future<?> future;
    private volatile boolean running;

    public ChangeStreamConsumer(
            StringRedisTemplate redisTemplate,
            ChangeStreamProperties properties,
            ChangeStreamEntryReader reader,
            LearnMessageOutcomePort learnMessageOutcomePort,
            @Qualifier("changeStreamExecutor") ExecutorService executorService,
            LoggerFactory loggerFactory) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.reader = reader;
        this.learnMessageOutcomePort = learnMessageOutcomePort;
        this.executorService = executorService;
        this.log = loggerFactory.getLogger(ChangeStreamConsumer.class);
    }

    @Override
    public void start() {
        running = true;
        // Runs synchronously so the group exists before start() returns, not merely before the background loop
        // gets around to it. An unreachable Redis must not fail application startup — the loop's own reconnect
        // handling in run() takes over.
        try {
            createGroup(redisTemplate.opsForStream());
        } catch (DataAccessException e) {
            log.warn("Change-stream consumer could not reach Redis at startup, will retry: {}", e.getMessage());
        }
        future = executorService.submit(this::run);
    }

    @Override
    public void stop() {
        running = false;
        Future<?> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
            future = null;
        }
    }

    @Override
    public boolean isRunning() {
        return future != null;
    }

    public void run() {
        String consumerName = resolveConsumerName();
        StreamOperations<String, String, String> streamOperations = redisTemplate.opsForStream();

        while (running) {
            try {
                createGroup(streamOperations);
                consume(streamOperations, consumerName);
            } catch (DataAccessException e) {
                log.warn("Change-stream consumer lost its Redis connection, reconnecting: {}", e.getMessage());
                sleep(RECONNECT_BACKOFF);
            }
        }
    }

    private String resolveConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Could not resolve this host's name, falling back to a fixed consumer name: {}", e.getMessage());
            return FALLBACK_CONSUMER_NAME;
        }
    }

    private void createGroup(StreamOperations<String, String, String> streamOperations) {
        try {
            streamOperations.createGroup(properties.key(), ReadOffset.latest(), GROUP);
        } catch (DataAccessException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause == null || !cause.contains(BUSYGROUP_MARKER)) {
                throw e;
            }
        }
    }

    private void consume(StreamOperations<String, String, String> streamOperations, String consumerName) {
        boolean skipClaim = false;

        while (running) {
            boolean retryNeeded = false;
            if (!skipClaim) {
                retryNeeded = processEntries(streamOperations, claimIdleEntries(streamOperations, consumerName), false);
            }

            if (!retryNeeded && running) {
                retryNeeded = processEntries(streamOperations, readOwnPending(streamOperations, consumerName), false);
            }

            if (!retryNeeded && running) {
                retryNeeded = processEntries(streamOperations, readNewEntries(streamOperations, consumerName), true);
            }

            skipClaim = retryNeeded;
            if (retryNeeded) {
                sleep(RETRY_BACKOFF);
            }
        }
    }

    private List<MapRecord<String, String, String>> claimIdleEntries(
            StreamOperations<String, String, String> streamOperations, String consumerName) {
        PendingMessages pendingMessages = streamOperations.pending(
                properties.key(), GROUP, Range.unbounded(), CLAIM_BATCH_SIZE, properties.claimIdle());
        List<RecordId> idleRecordIds =
                pendingMessages.stream().map(PendingMessage::getId).toList();

        if (idleRecordIds.isEmpty()) {
            return List.of();
        }

        return streamOperations.claim(
                properties.key(), GROUP, consumerName, properties.claimIdle(), idleRecordIds.toArray(RecordId[]::new));
    }

    private List<MapRecord<String, String, String>> readOwnPending(
            StreamOperations<String, String, String> streamOperations, String consumerName) {
        return streamOperations.read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty(),
                StreamOffset.create(properties.key(), ReadOffset.from("0")));
    }

    private List<MapRecord<String, String, String>> readNewEntries(
            StreamOperations<String, String, String> streamOperations, String consumerName) {
        return streamOperations.read(
                Consumer.from(GROUP, consumerName),
                StreamReadOptions.empty().block(NEW_ENTRIES_BLOCK),
                StreamOffset.create(properties.key(), ReadOffset.lastConsumed()));
    }

    // A fresh delivery stops at its first retry so a later, never-yet-offered entry cannot jump ahead of one
    // still waiting on its first attempt; a pending sweep (own pending, claimed) keeps going past a still-failing
    // entry instead, so a sibling already in the group's PEL is not held hostage to it indefinitely.
    private boolean processEntries(
            StreamOperations<String, String, String> streamOperations,
            List<MapRecord<String, String, String>> entries,
            boolean stopOnRetry) {
        boolean retryNeeded = false;
        for (MapRecord<String, String, String> entry : entries) {
            if (processEntry(streamOperations, entry)) {
                if (stopOnRetry) {
                    return true;
                }
                retryNeeded = true;
            }
        }
        return retryNeeded;
    }

    private boolean processEntry(
            StreamOperations<String, String, String> streamOperations, MapRecord<String, String, String> entry) {
        String entryId = entry.getId().getValue();

        Optional<LearnMessageOutcomeCommand> command;
        try {
            command = reader.read(entryId, entry.getValue());
        } catch (InvalidValueException e) {
            log.warn(
                    "Change-stream entry {} could not be read, acknowledging without applying: {}",
                    entryId,
                    e.getMessage());
            acknowledge(streamOperations, entryId);
            return false;
        }

        if (command.isEmpty()) {
            acknowledge(streamOperations, entryId);
            return false;
        }

        return offer(streamOperations, entryId, command.get());
    }

    private boolean offer(
            StreamOperations<String, String, String> streamOperations,
            String entryId,
            LearnMessageOutcomeCommand command) {
        LearnOutcome outcome;
        try {
            outcome = learnMessageOutcomePort.learn(command);
        } catch (RuntimeException e) {
            log.error(
                    "Learning the outcome of change-stream entry {} failed, leaving it pending: {}",
                    entryId,
                    e.getMessage());
            return true;
        }

        return switch (outcome) {
            case APPLIED, DROPPED -> {
                acknowledge(streamOperations, entryId);
                yield false;
            }
            case RETRY_LATER -> true;
        };
    }

    private void acknowledge(StreamOperations<String, String, String> streamOperations, String entryId) {
        streamOperations.acknowledge(properties.key(), GROUP, entryId);
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
