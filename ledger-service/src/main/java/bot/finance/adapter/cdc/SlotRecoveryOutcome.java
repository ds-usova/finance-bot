package bot.finance.adapter.cdc;

import java.util.Optional;

/**
 * What {@link ChangeStreamRecovery#recover()} answers, and what {@code CdcRecoveryEndpoint} maps to a response
 * status: a rebuilt slot carries both positions, a refusal carries neither.
 */
public record SlotRecoveryOutcome(Status status, Optional<String> abandonedPosition, Optional<String> resumedPosition) {

    public enum Status {
        REBUILT,
        SLOT_NOT_LOST,
        LOCK_HELD,
        ENGINE_DID_NOT_STOP,
        POSITION_NOT_DELETED,
        SLOT_NOT_DROPPED
    }
}
