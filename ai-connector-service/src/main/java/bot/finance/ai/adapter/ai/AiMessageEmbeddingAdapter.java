package bot.finance.ai.adapter.ai;

import bot.finance.ai.adapter.scheduling.MemoryProperties;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.domain.exception.MessageEmbeddingFailedException;
import bot.finance.ai.domain.value.Embedding;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

@Component
public class AiMessageEmbeddingAdapter implements MessageEmbeddingPort {

    private final EmbeddingModel embeddingModel;
    private final MemoryProperties properties;
    private final Logger log;
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

    public AiMessageEmbeddingAdapter(
            EmbeddingModel embeddingModel, MemoryProperties properties, LoggerFactory loggerFactory) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.log = loggerFactory.getLogger(AiMessageEmbeddingAdapter.class);
    }

    @Override
    public Embedding embed(String text) {
        return call(List.of(text), properties.embeddingTimeout()).get(0);
    }

    @Override
    public List<Embedding> embedAll(List<String> texts) {
        return call(texts, properties.backfillTimeout());
    }

    private List<Embedding> call(List<String> texts, Duration timeout) {
        Future<EmbeddingResponse> future = virtualThreads.submit(() -> embeddingModel.call(
                new EmbeddingRequest(texts, EmbeddingOptions.builder().build())));

        EmbeddingResponse response;
        try {
            response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new MessageEmbeddingFailedException("Provider did not answer within " + timeout, e);
        } catch (ExecutionException e) {
            throw new MessageEmbeddingFailedException("Failed to embed text via provider", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageEmbeddingFailedException("Interrupted while waiting for provider", e);
        }

        List<Embedding> embeddings = response.getResults().stream()
                .map(result -> new Embedding(toFloatList(result.getOutput())))
                .toList();

        if (embeddings.size() != texts.size()) {
            throw new MessageEmbeddingFailedException(
                    "Provider answered " + embeddings.size() + " vectors for " + texts.size() + " texts");
        }

        return embeddings;
    }

    private static List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}
