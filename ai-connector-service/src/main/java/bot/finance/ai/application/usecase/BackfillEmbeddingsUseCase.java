package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.UnembeddedMessage;
import bot.finance.ai.application.port.BackfillEmbeddingsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import java.time.Duration;
import java.util.List;

public class BackfillEmbeddingsUseCase implements BackfillEmbeddingsPort {

    private final MessageMemoryPort messageMemoryPort;
    private final MessageEmbedder messageEmbedder;
    private final int batch;
    private final int batches;
    private final Duration staleClaim;
    private final Logger log;

    public BackfillEmbeddingsUseCase(
            MessageMemoryPort messageMemoryPort,
            MessageEmbedder messageEmbedder,
            int batch,
            int batches,
            Duration staleClaim,
            LoggerFactory loggerFactory) {
        if (batch <= 0) {
            throw new InvalidValueException("batch must be positive");
        }
        if (batches <= 0) {
            throw new InvalidValueException("batches must be positive");
        }
        if (staleClaim.isZero() || staleClaim.isNegative()) {
            throw new InvalidValueException("staleClaim must be positive");
        }

        this.messageMemoryPort = messageMemoryPort;
        this.messageEmbedder = messageEmbedder;
        this.batch = batch;
        this.batches = batches;
        this.staleClaim = staleClaim;
        this.log = loggerFactory.getLogger(BackfillEmbeddingsUseCase.class);
    }

    @Override
    public void backfill() {
        log.debug("Backfilling the embeddings");
        for (int i = 0; i < batches; i++) {
            if (!runOneBatch()) {
                return;
            }
        }
    }

    /**
     * @return true if a claim was processed and the tick should continue, false if the tick must end
     */
    private boolean runOneBatch() {
        List<UnembeddedMessage> claim;
        try {
            claim = messageMemoryPort.claimUnembedded(batch, messageEmbedder.embeddingAttempts(), staleClaim);
        } catch (MessageStoreFailedException e) {
            log.warn("Claim failed, retrying on next run: {}", e.getMessage());
            return false;
        }

        if (claim.isEmpty()) {
            return false;
        }

        if (!messageEmbedder.ensureEmbedded(claim)) {
            return false;
        }
        return claim.size() == batch;
    }
}
