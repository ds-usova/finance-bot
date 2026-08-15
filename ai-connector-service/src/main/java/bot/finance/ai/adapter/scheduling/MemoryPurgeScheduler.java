package bot.finance.ai.adapter.scheduling;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.PurgeMessagesPort;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class MemoryPurgeScheduler {

    private final PurgeMessagesPort purgeMessagesPort;
    private final MemoryProperties properties;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Logger log;

    public MemoryPurgeScheduler(
            PurgeMessagesPort purgeMessagesPort,
            MemoryProperties properties,
            ScheduledExecutorService scheduledExecutorService,
            LoggerFactory loggerFactory) {
        this.purgeMessagesPort = purgeMessagesPort;
        this.properties = properties;
        this.scheduledExecutorService = scheduledExecutorService;
        this.log = loggerFactory.getLogger(MemoryPurgeScheduler.class);
    }

    public void run() {
        // calls the purge port once, inside a guard that logs any RuntimeException at ERROR so the timer never
        // dies
    }
}
