package bot.finance.ai.domain.value;

import java.util.Optional;

public record SpendingRowChange(
        SpendingKind kind,
        ChangeOperation op,
        String transactionId,
        Optional<SpendingRow> before,
        Optional<SpendingRow> after)
        implements RecordedChange {

    public SpendingRowChange {}

    public SpendingRow row() {
        // after where present, else before
        return null;
    }

    public Optional<MessageIdentity> messageIdentity() {
        // row().messageIdentity()
        return Optional.empty();
    }
}
