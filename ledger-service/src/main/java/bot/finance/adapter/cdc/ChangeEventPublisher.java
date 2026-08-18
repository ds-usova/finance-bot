package bot.finance.adapter.cdc;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.debezium.engine.ChangeEvent;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Forwards an outbox insert's four columns to the writer; resolves nothing.
 */
@Component
public class ChangeEventPublisher {

    private final RedisChangeStreamWriter redisChangeStreamWriter;
    private final ChangeStreamMeters meters;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChangeEventPublisher(RedisChangeStreamWriter redisChangeStreamWriter, ChangeStreamMeters meters) {
        this.redisChangeStreamWriter = redisChangeStreamWriter;
        this.meters = meters;
    }

    public boolean publish(ChangeEvent<String, String> event) {
        JsonNode after = readAfter(event);
        String id = after.get("id").asText();
        String type = after.get("type").asText();
        String occurredAt = after.get("occurred_at").asText();
        String payload = after.get("payload").asText();

        if (!redisChangeStreamWriter.write(id, type, occurredAt, payload)) {
            meters.countPublishFailure();
            return false;
        }

        meters.countPublished(type);
        meters.setEventLag(Instant.parse(occurredAt));
        return true;
    }

    private JsonNode readAfter(ChangeEvent<String, String> event) {
        try {
            return objectMapper.readTree(event.value()).get("after");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse change event value", e);
        }
    }
}
