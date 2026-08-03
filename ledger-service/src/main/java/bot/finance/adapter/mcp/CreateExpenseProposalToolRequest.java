package bot.finance.adapter.mcp;

public record CreateExpenseProposalToolRequest(
        String category,
        String parentCategory,
        String description,
        String merchant,
        String amount,
        String currencyCode) {}
