package bot.finance.adapter.mcp;

public record CreateExpenseProposalToolRequest(
        String category,
        String parentCategory,
        String description,
        String merchant,
        Long amountMinorUnits,
        String currencyCode) {}
