package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface MessageMemoryPort {

    /**
     * @throws MessageStoreFailedException if the store cannot be reached or the read fails
     */
    Optional<RegisteredMessage> find(MessageIdentity identity);

    /**
     * @throws MessageStoreFailedException if the store cannot be reached or the write fails
     */
    void storeEmbedding(long messageId, Embedding embedding);

    /**
     * @throws MessageStoreFailedException if the store cannot be reached or the write fails
     */
    int countEmbeddingAttempt(long messageId);

    /**
     * @throws MessageStoreFailedException if the store cannot be reached or the read fails
     */
    List<MessageExample> findExamples(ExampleQuery query);

    /**
     * @throws MessageStoreFailedException if the store cannot be reached or the write fails
     */
    List<UnembeddedMessage> claimUnembedded(int batch, int maxAttempts, Duration staleClaim);
}
