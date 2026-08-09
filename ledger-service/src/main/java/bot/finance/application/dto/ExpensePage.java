package bot.finance.application.dto;

import java.util.List;

public record ExpensePage(List<ExpenseEntry> items, int limit, int offset, long total, List<DayTotal> dayTotals) {

    public static ExpensePage of(List<ExpenseEntry> items, int limit, int offset, long total) {
        // groups the RECORDED items by the UTC day of createdAt, sums each day per currency, and answers one
        // DayTotal per such day, newest first
        return new ExpensePage(items, limit, offset, total, List.of());
    }
}
