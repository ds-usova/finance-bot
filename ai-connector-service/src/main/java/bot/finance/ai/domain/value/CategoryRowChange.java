package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;

public record CategoryRowChange(ChangeOperation op, Optional<CategoryRow> before, Optional<CategoryRow> after)
        implements RecordedChange {

    public CategoryRowChange {
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
}
