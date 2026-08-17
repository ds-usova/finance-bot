package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.Embedding;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public boolean embedAndStoreAll(List<UnembeddedMessage> claim) {
        List<String> texts = claim.stream().map(UnembeddedMessage::text).collect(Collectors.toList());
        List<Embedding> vectors;
        try {
            vectors = messageEmbeddingPort.embedAll(texts);
        } catch (MessageEmbeddingFailedException e) {
            log.warn("Embedding failed, counting an attempt on every claimed row: {}", e.getMessage());
            countAttempts(claim);
            return false;
        }

        if (vectors.size() < claim.size()) {
            log.warn("Embedding provider answered fewer vectors than the batch held");
            countAttempts(claim);
            return false;
        }

        for (int i = 0; i < claim.size(); i++) {
            try {
                store(claim.get(i).messageId(), vectors.get(i));
            } catch (MessageStoreFailedException e) {
                log.warn("Storing embedding failed, retrying on next run: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }

    private void countAttempts(List<UnembeddedMessage> claim) {
        for (UnembeddedMessage row : claim) {
            countFailure(row.messageId());
        }
    }

    private void countFailure(long messageId) {
        int attempts = messageMemoryPort.countEmbeddingAttempt(messageId);
        if (attempts == embeddingAttempts) {
            log.error("Giving up embedding row {} after repeated failures", messageId);
        }
    }

    private void store(long messageId, Embedding embedding) {
        messageMemoryPort.storeEmbedding(messageId, embedding);
    }
}
