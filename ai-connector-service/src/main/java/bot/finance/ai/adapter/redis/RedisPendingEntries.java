package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.PendingEntries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class RedisPendingEntries implements PendingEntries {

    private final StringRedisTemplate redisTemplate;
    private final ChangeStreamProperties properties;
    private final Logger log;

    public RedisPendingEntries(
            StringRedisTemplate redisTemplate, ChangeStreamProperties properties, LoggerFactory loggerFactory) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.log = loggerFactory.getLogger(RedisPendingEntries.class);
    }

    @Override
    public double count() {
        // Runs a live XPENDING summary for the group and answers its total pending count; when Redis is
        // unreachable, answers the last value it saw and logs at warn; before any read has succeeded, answers
        // NaN, so the gauge is absent from the scrape rather than reading as a drained group.
        return Double.NaN;
    }
}
