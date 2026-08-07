package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidExpenseFilterException;

public record ExpenseFilter(ExpenseStatus status, Long categoryId, SpendingPeriod period, int limit, int offset) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public ExpenseFilter {
        if (limit < 1) {
            throw new InvalidExpenseFilterException("limit must be at least 1");
        }
        if (limit > MAX_LIMIT) {
            throw new InvalidExpenseFilterException("limit must be at most " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new InvalidExpenseFilterException("offset must not be negative");
        }
    }
}
