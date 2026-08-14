package bot.finance.adapter.cdc;

import bot.finance.adapter.persistence.ReplicationCatalogue;
import bot.finance.adapter.persistence.ReplicationSlotRetention;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Reads {@code pg_replication_slots} for the configured slot on a timer and records its retained size and
 * {@code wal_status}, whether or not this instance's engine holds the slot — the monitor is bound to the
 * slot's existence, not to the engine's. Scheduled through {@link SmartLifecycle} the same way
 * {@link ChangeStreamLifecycle} drives the reader, but unconditionally: a slot left behind by capture being
 * switched off is exactly the state this monitor exists to reveal.
 */
@Component
public class ReplicationSlotMonitor implements SmartLifecycle {

    private final ReplicationCatalogue replicationCatalogue;
    private final CdcProperties properties;
    private final ChangeStreamMeters meters;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Logger log;

    private volatile ScheduledFuture<?> scheduledFuture;

    public ReplicationSlotMonitor(
            ReplicationCatalogue replicationCatalogue,
            CdcProperties properties,
            ChangeStreamMeters meters,
            ScheduledExecutorService scheduledExecutorService,
            LoggerFactory loggerFactory) {
        this.replicationCatalogue = replicationCatalogue;
        this.properties = properties;
        this.meters = meters;
        this.scheduledExecutorService = scheduledExecutorService;
        this.log = loggerFactory.getLogger(ReplicationSlotMonitor.class);
    }

    @Override
    public void start() {
        long intervalMillis = properties.slotMonitorInterval().toMillis();
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::readSlotSafely, 0, intervalMillis, TimeUnit.MILLISECONDS);
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

    private void readSlotSafely() {
        try {
            readSlot();
        } catch (RuntimeException e) {
            log.error("Failed to read replication slot {} on schedule", properties.slotName(), e);
        }
    }

    public void readSlot() {
        Optional<ReplicationSlotRetention> retention = replicationCatalogue.findSlotRetention(properties.slotName());

        if (retention.isEmpty()) {
            meters.setSlotRetainedBytes(0);
            meters.setSlotWalStatus(ReplicationSlotState.ABSENT);
            return;
        }

        meters.setSlotRetainedBytes(retention.get().retainedBytes());
        meters.setSlotWalStatus(
                ReplicationSlotState.fromNullableWalStatus(retention.get().walStatus()));
    }
}
