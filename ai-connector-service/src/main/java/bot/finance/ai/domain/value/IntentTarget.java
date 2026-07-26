package bot.finance.ai.domain.value;

import java.util.Optional;

public enum IntentTarget {

    CATEGORY,
    EXPENSE;

    public static Optional<IntentTarget> fromLabel(String label) {
        // matches label, trimmed and case-insensitively, against CATEGORY/EXPENSE, returning
        // Optional.empty() for null, blank, or unrecognized input rather than throwing
        return null;
    }

}
