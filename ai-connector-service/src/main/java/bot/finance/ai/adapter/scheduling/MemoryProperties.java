package bot.finance.ai.adapter.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("memory")
public record MemoryProperties(
        boolean enabled,
        Duration maxAge,
        Duration purgeInterval,
        int purgeBatch,
        int entryAttempts,
        Duration embeddingTimeout,
        int embeddingAttempts,
        int examples,
        double minSimilarity,
        Duration recentWindow,
        int exampleLines,
        int backfillBatch,
        int backfillBatches,
        Duration backfillTimeout) {}
