package bot.finance.ai.application.usecase;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.value.Embedding;
import java.util.Optional;

public class MessageEmbedder {

    private final MessageMemoryPort messageMemoryPort;
    private final MessageEmbeddingPort messageEmbeddingPort;
    private final int embeddingAttempts;
    private final Logger log;

    public MessageEmbedder(
            MessageMemoryPort messageMemoryPort,
            MessageEmbeddingPort messageEmbeddingPort,
            int embeddingAttempts,
            LoggerFactory loggerFactory) {
        if (embeddingAttempts <= 0) {
            throw new InvalidValueException("embeddingAttempts must be positive");
        }

        this.messageMemoryPort = messageMemoryPort;
        this.messageEmbeddingPort = messageEmbeddingPort;
        this.embeddingAttempts = embeddingAttempts;
        this.log = loggerFactory.getLogger(MessageEmbedder.class);
    }

    public int embeddingAttempts() {
        return embeddingAttempts;
    }

    public Optional<Embedding> embedAndStore(long messageId, String text) {
        try {
            Embedding computed = messageEmbeddingPort.embed(text);
            messageMemoryPort.storeEmbedding(messageId, computed);
            return Optional.of(computed);
        } catch (MessageEmbeddingFailedException e) {
            log.warn("Embedding failed for message {}: {}", messageId, e.getMessage());
            countFailure(messageId);
            return Optional.empty();
        }
    }

    public void countFailure(long messageId) {
        int attempts = messageMemoryPort.countEmbeddingAttempt(messageId);
        if (attempts == embeddingAttempts) {
            log.error("Giving up embedding row {} after repeated failures", messageId);
        }
    }

    public void store(long messageId, Embedding embedding) {
        messageMemoryPort.storeEmbedding(messageId, embedding);
    }
}
