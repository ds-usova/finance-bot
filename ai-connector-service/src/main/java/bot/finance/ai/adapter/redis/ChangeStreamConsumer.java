package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class ChangeStreamConsumer implements SmartLifecycle {

    private final StringRedisTemplate redisTemplate;
    private final ChangeStreamProperties properties;
    private final ChangeStreamEntryReader reader;
    private final LearnMessageOutcomePort learnMessageOutcomePort;
    private final ExecutorService executorService;
    private final Logger log;

    private volatile Future<?> future;

    public ChangeStreamConsumer(
            StringRedisTemplate redisTemplate,
            ChangeStreamProperties properties,
            ChangeStreamEntryReader reader,
            LearnMessageOutcomePort learnMessageOutcomePort,
            @Qualifier("changeStreamExecutor") ExecutorService executorService,
            LoggerFactory loggerFactory) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.reader = reader;
        this.learnMessageOutcomePort = learnMessageOutcomePort;
        this.executorService = executorService;
        this.log = loggerFactory.getLogger(ChangeStreamConsumer.class);
    }

    @Override
    public void start() {
        future = executorService.submit(this::run);
    }

    @Override
    public void stop() {
        Future<?> currentFuture = future;
        if (currentFuture != null) {
            currentFuture.cancel(true);
            future = null;
        }
    }

    @Override
    public boolean isRunning() {
        return future != null;
    }

    public void run() {
        // creates the group on the stream, then reads claimed, pending and new entries in turn, hands each to the
        // reader and the port, and acknowledges what was applied or dropped
    }
}
