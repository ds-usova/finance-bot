package bot.finance.application.dto;

import java.util.List;

public record ExpensePage(List<ExpenseEntry> items, int limit, int offset, long total) {}
