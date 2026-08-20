package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.PendingEntries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class RedisPendingEntries implements PendingEntries {

    private final StringRedisTemplate redisTemplate;
    private final ChangeStreamProperties properties;
    private final Logger log;

    private volatile double lastSeen = Double.NaN;

    public RedisPendingEntries(
            StringRedisTemplate redisTemplate, ChangeStreamProperties properties, LoggerFactory loggerFactory) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.log = loggerFactory.getLogger(RedisPendingEntries.class);
    }

    @Override
    public double count() {
        try {
            lastSeen = redisTemplate
                    .opsForStream()
                    .pending(properties.key(), ChangeStreamProperties.GROUP)
                    .getTotalPendingMessages();
        } catch (DataAccessException e) {
            log.warn(
                    "Could not reach Redis for the pending-entries count, answering the last seen value: {}",
                    e.getMessage());
        }
        return lastSeen;
    }
}
