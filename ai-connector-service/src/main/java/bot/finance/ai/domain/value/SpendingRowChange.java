package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;

public record SpendingRowChange(
        SpendingKind kind,
        ChangeOperation op,
        String transactionId,
        Optional<SpendingRow> before,
        Optional<SpendingRow> after)
        implements RecordedChange {

    public SpendingRowChange {
        if (transactionId == null || transactionId.isBlank()) {
            throw new InvalidValueException("Transaction id must not be null or blank");
        }
        if (kind == null) {
            throw new InvalidValueException("Kind must not be null");
        }
        if (op == null) {
            throw new InvalidValueException("Op must not be null");
        }
        if (before == null || after == null) {
            throw new InvalidValueException("Before and after must not be null");
        }
        if (op == ChangeOperation.CREATED && after.isEmpty()) {
            throw new InvalidValueException("Created change must carry an after row");
        }
        if (op == ChangeOperation.DELETED && before.isEmpty()) {
            throw new InvalidValueException("Deleted change must carry a before row");
        }
        if (op == ChangeOperation.UPDATED && (before.isEmpty() || after.isEmpty())) {
            throw new InvalidValueException("Updated change must carry both before and after rows");
        }
    }

    public SpendingRow row() {
        return after.orElseGet(() -> before.orElseThrow());
    }

    public Optional<MessageIdentity> messageIdentity() {
        return row().messageIdentity();
    }
}
