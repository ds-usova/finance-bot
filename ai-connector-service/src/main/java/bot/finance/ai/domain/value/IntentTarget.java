package bot.finance.ai.domain.value;

import java.util.Arrays;
import java.util.Optional;

public enum IntentTarget {

    CATEGORY,
    EXPENSE;

    public static Optional<IntentTarget> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }

        String normalized = label.trim();
        return Arrays.stream(values())
                .filter(target -> target.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

}
