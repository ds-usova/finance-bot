package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.dto.ExampleQuery;
import bot.finance.ai.application.dto.RegisteredMessage;
import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.domain.value.Embedding;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "false")
public class NoMessageMemoryAdapter implements MessageMemoryPort {

    @Override
    public Optional<RegisteredMessage> find(MessageIdentity identity) {
        return Optional.empty();
    }

    @Override
    public void storeEmbedding(long messageId, Embedding embedding) {}

    @Override
    public int countEmbeddingAttempt(long messageId) {
        return 0;
    }

    @Override
    public List<MessageExample> findExamples(ExampleQuery query) {
        return List.of();
    }

    @Override
    public List<UnembeddedMessage> claimUnembedded(int batch, int maxAttempts, Duration staleClaim) {
        return List.of();
    }
}
