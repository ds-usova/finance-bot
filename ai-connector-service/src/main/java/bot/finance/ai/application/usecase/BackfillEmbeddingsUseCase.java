package bot.finance.ai.application.usecase;

import bot.finance.ai.application.port.BackfillEmbeddingsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import java.time.Duration;

public class BackfillEmbeddingsUseCase implements BackfillEmbeddingsPort {

    private final MessageMemoryPort messageMemoryPort;
    private final MessageEmbeddingPort messageEmbeddingPort;
    private final int batch;
    private final int batches;
    private final int embeddingAttempts;
    private final Duration staleClaim;
    private final Logger log;

    public BackfillEmbeddingsUseCase(
            MessageMemoryPort messageMemoryPort,
            MessageEmbeddingPort messageEmbeddingPort,
            int batch,
            int batches,
            int embeddingAttempts,
            Duration staleClaim,
            LoggerFactory loggerFactory) {
        this.messageMemoryPort = messageMemoryPort;
        this.messageEmbeddingPort = messageEmbeddingPort;
        this.batch = batch;
        this.batches = batches;
        this.embeddingAttempts = embeddingAttempts;
        this.staleClaim = staleClaim;
        this.log = loggerFactory.getLogger(BackfillEmbeddingsUseCase.class);
    }

    @Override
    public void backfill() {
        // claims up to batch unembedded rows per call, up to batches calls, embeds each claim in one provider
        // call holding no store lock, and writes the vectors back; a provider or store failure counts an
        // attempt on every claimed row of that batch and ends the tick
    }
}
