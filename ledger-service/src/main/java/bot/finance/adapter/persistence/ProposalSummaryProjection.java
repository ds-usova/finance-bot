package bot.finance.adapter.persistence;

public record ProposalSummaryProjection(
        String categoryName,
        String parentName,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode) {}
