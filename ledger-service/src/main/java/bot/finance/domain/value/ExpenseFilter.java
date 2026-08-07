package bot.finance.domain.value;

public record ExpenseFilter(ExpenseStatus status, Long categoryId, SpendingPeriod period, int limit, int offset) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public ExpenseFilter {
        // TODO: refuse a limit below 1 or above MAX_LIMIT, and a negative offset, with
        // InvalidExpenseFilterException naming the parameter and the bound it broke
    }
}
