package bot.finance.adapter.persistence;

import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UniqueViolations {

    // Postgres SQLState for a unique violation.
    private static final String UNIQUE_VIOLATION = "23505";
    private static final Pattern CONSTRAINT_NAME_PATTERN = Pattern.compile("constraint \"([^\"]+)\"");

    private UniqueViolations() {}

    // The constraint name is not exposed as a structured field anywhere in the exception chain -
    // Postgres reports it only inside the SQLException's message text.
    static String constraintName(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                return constraintNameFromMessage(sqlException.getMessage());
            }
        }
        return null;
    }

    private static String constraintNameFromMessage(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = CONSTRAINT_NAME_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
