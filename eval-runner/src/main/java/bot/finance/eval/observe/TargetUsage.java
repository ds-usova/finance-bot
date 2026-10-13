package bot.finance.eval.observe;

/**
 * What the services counted between two scrapes around a target turn. A value is null where either scrape of its
 * service failed.
 */
public record TargetUsage(
        Double providerChatCalls,
        Double providerChatSeconds,
        Double providerEmbeddingCalls,
        Double inputTokens,
        Double outputTokens,
        Double ledgerToolCalls) {}
