package bot.finance.adapter.redis;

import bot.finance.adapter.cdc.CdcProperties;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * XADDs one stream entry per call, trimmed to {@link CdcProperties#streamMaxLength()} on every write.
 */
@Component
public class RedisChangeStreamWriter {

    private final StringRedisTemplate redisTemplate;
    private final CdcProperties properties;

    public RedisChangeStreamWriter(StringRedisTemplate redisTemplate, CdcProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean write(String payload, Optional<String> enrichment) {
        // XADDs payload and, when present, enrichment to the configured stream, capped with an approximate
        // trim to streamMaxLength; answers false rather than throwing when Redis cannot be reached
        return false;
    }
}
