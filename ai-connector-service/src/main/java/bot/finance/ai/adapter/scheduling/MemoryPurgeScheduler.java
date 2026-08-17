package bot.finance.ai.adapter.scheduling;

import bot.finance.ai.application.port.BackfillEmbeddingsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.PurgeMessagesPort;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class MemoryPurgeScheduler implements SmartLifecycle {

    private final PurgeMessagesPort purgeMessagesPort;
    private final BackfillEmbeddingsPort backfillEmbeddingsPort;
    private final MemoryProperties properties;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Logger log;

    private volatile ScheduledFuture<?> scheduledFuture;

    public MemoryPurgeScheduler(
            PurgeMessagesPort purgeMessagesPort,
            BackfillEmbeddingsPort backfillEmbeddingsPort,
            MemoryProperties properties,
            ScheduledExecutorService scheduledExecutorService,
            LoggerFactory loggerFactory) {
        this.purgeMessagesPort = purgeMessagesPort;
        this.backfillEmbeddingsPort = backfillEmbeddingsPort;
        this.properties = properties;
        this.scheduledExecutorService = scheduledExecutorService;
        this.log = loggerFactory.getLogger(MemoryPurgeScheduler.class);
    }

    @Override
    public void start() {
        long intervalMillis = properties.purgeInterval().toMillis();
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::runSafely, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        ScheduledFuture<?> currentFuture = scheduledFuture;
        if (currentFuture != null) {
            currentFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    @Override
    public boolean isRunning() {
        return scheduledFuture != null;
    }

    public void run() {
        purgeMessagesPort.purge();
        backfillEmbeddingsPort.backfill();
    }

    private void runSafely() {
        try {
            run();
        } catch (RuntimeException e) {
            log.error("Failed to purge messages on schedule", e);
        }
    }
}
