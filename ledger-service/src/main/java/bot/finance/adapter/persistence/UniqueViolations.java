package bot.finance.adapter.persistence;

final class UniqueViolations {

    private UniqueViolations() {}

    static String constraintName(Throwable e) {
        // stub-intent: the constraint a unique violation (SQLState 23505) names in its message, found anywhere in
        // the exception chain; null when the chain holds no unique violation.
        return null;
    }
}
