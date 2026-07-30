package bot.finance.adapter.mcp;

import org.springframework.ai.mcp.annotation.McpToolParam;

public record CreateExpenseProposalToolRequest(
        @McpToolParam(description = "the category's name - one filed under a grouping, never a grouping")
                String category,
        @McpToolParam(
                        required = false,
                        description = "the grouping's name, optional - only to break a tie between categories "
                                + "sharing the same name")
                String parentCategory,
        @McpToolParam(description = "what was bought") String description,
        @McpToolParam(required = false, description = "who it was bought from, optional - null or blank is none")
                String merchant,
        @McpToolParam(description = "the amount in the currency's minor units, required") Long amountMinorUnits,
        @McpToolParam(description = "ISO 4217, three letters") String currencyCode) {}
