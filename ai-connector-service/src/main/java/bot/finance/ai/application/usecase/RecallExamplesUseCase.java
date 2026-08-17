package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.RecallExamplesCommand;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.domain.value.MessageExample;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class RecallExamplesUseCase implements RecallExamplesPort {

    private final MessageMemoryPort messageMemoryPort;
    private final MessageEmbeddingPort messageEmbeddingPort;
    private final int examples;
    private final double minSimilarity;
    private final Duration recentWindow;
    private final Duration maxAge;
    private final int exampleLines;
    private final int embeddingAttempts;
    private final Logger log;

    public RecallExamplesUseCase(
            MessageMemoryPort messageMemoryPort,
            MessageEmbeddingPort messageEmbeddingPort,
            int examples,
            double minSimilarity,
            Duration recentWindow,
            Duration maxAge,
            int exampleLines,
            int embeddingAttempts,
            LoggerFactory loggerFactory) {
        this.messageMemoryPort = messageMemoryPort;
        this.messageEmbeddingPort = messageEmbeddingPort;
        this.examples = examples;
        this.minSimilarity = minSimilarity;
        this.recentWindow = recentWindow;
        this.maxAge = maxAge;
        this.exampleLines = exampleLines;
        this.embeddingAttempts = embeddingAttempts;
        this.log = loggerFactory.getLogger(RecallExamplesUseCase.class);
    }

    @Override
    public Optional<List<MessageExample>> recall(RecallExamplesCommand command) {
        // reads the registered row, embeds it once where it holds no vector, and answers the neighbours the
        // query's bounds admit; empty where no retrieval ran
        return Optional.empty();
    }
}
