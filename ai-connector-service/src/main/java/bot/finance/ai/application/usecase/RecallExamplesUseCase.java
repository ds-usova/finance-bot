package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RecallExamplesCommand;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class RecallExamplesUseCase implements RecallExamplesPort {

    private final MessageMemoryPort messageMemoryPort;
    private final MessageEmbedder messageEmbedder;
    private final int examples;
    private final double minSimilarity;
    private final Duration recentWindow;
    private final Duration maxAge;
    private final int exampleLines;
    private final Logger log;

    public RecallExamplesUseCase(
            MessageMemoryPort messageMemoryPort,
            MessageEmbedder messageEmbedder,
            int examples,
            double minSimilarity,
            Duration recentWindow,
            Duration maxAge,
            int exampleLines,
            LoggerFactory loggerFactory) {
        if (examples <= 0) {
            throw new InvalidValueException("examples must be positive");
        }
        if (exampleLines <= 0) {
            throw new InvalidValueException("exampleLines must be positive");
        }
        if (recentWindow.isZero() || recentWindow.isNegative()) {
            throw new InvalidValueException("recentWindow must be positive");
        }
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new InvalidValueException("maxAge must be positive");
        }
        if (minSimilarity < 0 || minSimilarity > 1) {
            throw new InvalidValueException("minSimilarity must be within [0,1]");
        }

        this.messageMemoryPort = messageMemoryPort;
        this.messageEmbedder = messageEmbedder;
        this.examples = examples;
        this.minSimilarity = minSimilarity;
        this.recentWindow = recentWindow;
        this.maxAge = maxAge;
        this.exampleLines = exampleLines;
        this.log = loggerFactory.getLogger(RecallExamplesUseCase.class);
    }

    @Override
    public Optional<List<MessageExample>> recall(RecallExamplesCommand command) {
        MessageIdentity identity = command.identity();
        try {
            Optional<RegisteredMessage> registered = messageMemoryPort.find(identity);
            if (registered.isEmpty()) {
                return Optional.empty();
            }

            RegisteredMessage row = registered.get();
            Optional<Embedding> vector =
                    row.embedding().or(() -> messageEmbedder.ensureEmbedded(row.messageId(), command.text()));

            return vector.map(embedding -> findExamples(identity, row.messageId(), embedding));
        } catch (MessageStoreFailedException e) {
            log.warn(
                    "Failed to recall examples for message {} of user {}",
                    identity.incomingMessageId(),
                    identity.userId());
            return Optional.empty();
        }
    }

    private List<MessageExample> findExamples(MessageIdentity identity, long messageId, Embedding vector) {
        ExampleQuery query = new ExampleQuery(
                identity.userId(), messageId, vector, examples, minSimilarity, recentWindow, maxAge, exampleLines);
        return messageMemoryPort.findExamples(query);
    }
}
