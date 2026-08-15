package bot.finance.adapter.cdc;

import java.util.Optional;

/**
 * What {@link ChangeStreamRecovery#recover()} answers, and what {@code CdcRecoveryEndpoint} maps to a response
 * status: a rebuilt slot carries both positions, a refusal carries neither.
 */
public record SlotRecoveryOutcome(Status status, Optional<String> abandonedPosition, Optional<String> resumedPosition) {

    public static SlotRecoveryOutcome refusal(Status status) {
        return new SlotRecoveryOutcome(status, Optional.empty(), Optional.empty());
    }

    /** The abandoned position stays optional: a slot that was already gone leaves none behind. */
    public static SlotRecoveryOutcome rebuilt(Optional<String> abandonedPosition, String resumedPosition) {
        return new SlotRecoveryOutcome(Status.REBUILT, abandonedPosition, Optional.of(resumedPosition));
    }

    public enum Status {
        REBUILT,
        SLOT_NOT_LOST,
        LOCK_HELD,
        ENGINE_DID_NOT_STOP,
        POSITION_NOT_DELETED,
        SLOT_NOT_DROPPED
    }
}
