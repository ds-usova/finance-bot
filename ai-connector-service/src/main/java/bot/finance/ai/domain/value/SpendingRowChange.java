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

        RecordedChange.requireSidesFor(op, before, after);
    }

    public SpendingRow row() {
        return after.orElseGet(() -> before.orElseThrow());
    }

    @Override
    public long rowId() {
        return row().id();
    }

    public Optional<MessageIdentity> messageIdentity() {
        return row().messageIdentity();
    }
}
