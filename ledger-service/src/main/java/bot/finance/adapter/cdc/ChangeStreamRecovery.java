package bot.finance.adapter.cdc;

import bot.finance.adapter.persistence.ReplicationCatalogue;
import bot.finance.adapter.persistence.ReplicationSlotPosition;
import bot.finance.adapter.persistence.SlotRebuildSession;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rebuilds an invalidated replication slot: stops {@link ChangeStreamReader}, deletes the stored position,
 * drops the slot and starts a fresh engine at the current end of the log — gated by an advisory lock so only
 * one instance runs the sequence at a time.
 */
@Component
public class ChangeStreamRecovery {

    private static final Duration ENGINE_STOP_TIMEOUT = Duration.ofSeconds(10);

    private final ChangeStreamReader changeStreamReader;
    private final ReplicationCatalogue replicationCatalogue;
    private final CdcProperties properties;
    private final Logger log;

    public ChangeStreamRecovery(
            ChangeStreamReader changeStreamReader,
            ReplicationCatalogue replicationCatalogue,
            CdcProperties properties,
            LoggerFactory loggerFactory) {
        this.changeStreamReader = changeStreamReader;
        this.replicationCatalogue = replicationCatalogue;
        this.properties = properties;
        this.log = loggerFactory.getLogger(ChangeStreamRecovery.class);
    }

    public SlotRecoveryOutcome recover() {
        return replicationCatalogue
                .underSlotLock(properties.slotName(), this::runSequence)
                .orElseGet(() -> SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.LOCK_HELD));
    }

    private SlotRecoveryOutcome runSequence(SlotRebuildSession session) {
        Optional<ReplicationSlotPosition> slot = session.findSlot();

        if (slot.isPresent()
                && ReplicationSlotState.fromNullableWalStatus(slot.get().walStatus()) != ReplicationSlotState.LOST) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.SLOT_NOT_LOST);
        }

        Optional<String> abandonedPosition = slot.map(ReplicationSlotPosition::confirmedFlushLsn);

        if (!changeStreamReader.stop(ENGINE_STOP_TIMEOUT)) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.ENGINE_DID_NOT_STOP);
        }

        if (!session.deleteStoredPosition()) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.POSITION_NOT_DELETED);
        }

        if (slot.isPresent() && !session.dropSlot()) {
            return SlotRecoveryOutcome.refusal(SlotRecoveryOutcome.Status.SLOT_NOT_DROPPED);
        }

        abandonedPosition.ifPresent(
                position -> log.error("Abandoned replication position {} at {}", position, Instant.now()));

        String resumedPosition = session.currentWalLsn();
        changeStreamReader.start();

        return SlotRecoveryOutcome.rebuilt(abandonedPosition, resumedPosition);
    }
}
