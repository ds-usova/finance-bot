package bot.finance.ai.adapter.ai;

import bot.finance.ai.adapter.scheduling.MemoryProperties;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.domain.value.Embedding;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class AiMessageEmbeddingAdapter implements MessageEmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final MemoryProperties properties;
    private final Logger log;

    public AiMessageEmbeddingAdapter(EmbeddingModel embeddingModel, MemoryProperties properties, LoggerFactory loggerFactory) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.log = loggerFactory.getLogger(AiMessageEmbeddingAdapter.class);
    }

    @Override
    public Embedding embed(String text) {
        // runs the call on a virtual thread and waits embeddingTimeout for it, so a slow provider costs the turn
        // that much and no more; a RuntimeException, a timeout or a mismatched vector count becomes
        // MessageEmbeddingFailedException
        return null;
    }

    @Override
    public List<Embedding> embedAll(List<String> texts) {
        // sends the whole batch in one call, on the same mechanism, and waits backfillTimeout for it
        return List.of();
    }
}
