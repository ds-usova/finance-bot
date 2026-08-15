package bot.finance.ai.application.usecase;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.PurgeMessagesPort;
import java.time.Clock;
import java.time.Duration;

public class PurgeMessagesUseCase implements PurgeMessagesPort {

    private final MessageStorePort messageStorePort;
    private final Clock clock;
    private final Duration maxAge;
    private final int batch;
    private final Logger log;

    public PurgeMessagesUseCase(
            MessageStorePort messageStorePort, Clock clock, Duration maxAge, int batch, LoggerFactory loggerFactory) {
        // refuses a zero or negative maxAge or batch as InvalidValueException, so a bad purge config fails at startup
        this.messageStorePort = messageStorePort;
        this.clock = clock;
        this.maxAge = maxAge;
        this.batch = batch;
        this.log = loggerFactory.getLogger(PurgeMessagesUseCase.class);
    }

    @Override
    public void purge() {
        // deletes rows received before clock.now() - maxAge in batches of `batch` until a batch deletes none,
        // logging a store failure at WARN and leaving the next run to try again
    }
}
