package bot.finance.adapter.mcp;

import java.time.Instant;

public record CreateExpenseProposalToolResponse(
        long id,
        String category,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode,
        Instant createdAt) {}
