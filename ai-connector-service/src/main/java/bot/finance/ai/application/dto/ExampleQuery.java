package bot.finance.ai.application.dto;

import bot.finance.ai.domain.value.Embedding;
import java.time.Duration;

public record ExampleQuery(
        long userId,
        long messageId,
        Embedding embedding,
        int examples,
        double minSimilarity,
        Duration recentWindow,
        Duration maxAge,
        int exampleLines) {}
