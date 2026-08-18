package bot.finance.adapter.cdc;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import io.debezium.engine.ChangeEvent;
import org.springframework.stereotype.Component;

/**
 * Forwards an outbox insert's four columns to the writer; resolves nothing.
 */
@Component
public class ChangeEventPublisher {

    private final RedisChangeStreamWriter redisChangeStreamWriter;
    private final ChangeStreamMeters meters;

    public ChangeEventPublisher(RedisChangeStreamWriter redisChangeStreamWriter, ChangeStreamMeters meters) {
        this.redisChangeStreamWriter = redisChangeStreamWriter;
        this.meters = meters;
    }

    public boolean publish(ChangeEvent<String, String> event) {
        // TODO: forward the outbox insert's four columns - id, type, occurred_at, payload - to the writer
        return false;
    }
}
