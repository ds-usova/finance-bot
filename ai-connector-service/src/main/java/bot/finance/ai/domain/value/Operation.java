package bot.finance.ai.domain.value;

import java.util.Optional;

public enum Operation {

    CREATE,
    READ,
    UPDATE,
    DELETE;

    public static Optional<Operation> fromLabel(String label) {
        // matches label, trimmed and case-insensitively, against each constant's name, returning
        // Optional.empty() for null, blank, or unrecognized input rather than throwing
        return null;
    }

}
