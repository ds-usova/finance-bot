package bot.finance.ai.application.usecase;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.PurgeMessagesPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class PurgeMessagesUseCase implements PurgeMessagesPort {

    private final MessageStorePort messageStorePort;
    private final Clock clock;
    private final Duration maxAge;
    private final int batch;
    private final Logger log;

    public PurgeMessagesUseCase(
            MessageStorePort messageStorePort, Clock clock, Duration maxAge, int batch, LoggerFactory loggerFactory) {
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new InvalidValueException("maxAge must be positive");
        }
        if (batch <= 0) {
            throw new InvalidValueException("batch must be positive");
        }

        this.messageStorePort = messageStorePort;
        this.clock = clock;
        this.maxAge = maxAge;
        this.batch = batch;
        this.log = loggerFactory.getLogger(PurgeMessagesUseCase.class);
    }

    @Override
    public void purge() {
        Instant cut = clock.instant().minus(maxAge);

        try {
            int deleted;
            do {
                deleted = messageStorePort.deleteReceivedBefore(cut, batch);
            } while (deleted > 0);
        } catch (MessageStoreFailedException e) {
            log.warn("Purge failed, retrying on next run: {}", e.getMessage());
        }
    }
}
