package bot.finance.adapter.mcp;

public record CreateExpenseProposalToolRequest(
        String category, String grouping, String description, String merchant, String amount, String currencyCode) {}
