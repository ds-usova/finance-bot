package bot.finance.ai.domain.value;

import java.util.Arrays;
import java.util.Optional;

public enum Operation {

    CREATE,
    READ,
    UPDATE,
    DELETE;

    public static Optional<Operation> fromLabel(String label) {
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String normalized = label.trim();
        return Arrays.stream(values())
                .filter(operation -> operation.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

}
