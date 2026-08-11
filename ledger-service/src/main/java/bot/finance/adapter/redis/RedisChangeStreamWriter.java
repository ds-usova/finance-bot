package bot.finance.adapter.redis;

import bot.finance.adapter.cdc.CdcProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStreamCommands.TrimOptions;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
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
        Map<String, String> body = new LinkedHashMap<>();
        body.put("payload", payload);
        enrichment.ifPresent(value -> body.put("enrichment", value));

        XAddOptions options = XAddOptions.trim(
                TrimOptions.maxLen(properties.streamMaxLength()).approximate());

        try {
            redisTemplate.opsForStream().add(properties.streamKey(), body, options);
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }
}
