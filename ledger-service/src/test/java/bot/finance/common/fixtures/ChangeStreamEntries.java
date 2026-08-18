package bot.finance.common.fixtures;

import bot.finance.common.containers.RedisContainers;
import com.fasterxml.jackson.databind.JsonNode;
import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;

/**
 * Reads entries back off {@code ledger.cdc} through the test Redis connection, so a capture test asserts against
 * the rows one person's turn produced rather than against a database-wide slot on a shared container.
 */
public class ChangeStreamEntries {

    private static final String ID_FIELD = "id";
    private static final String TYPE_FIELD = "type";
    private static final String OCCURRED_AT_FIELD = "occurredAt";
    private static final String PAYLOAD_FIELD = "payload";

    private ChangeStreamEntries() {}

    /**
     * Every entry on one named stream, in the order they were XADDed. Capture tests share the application's own
     * {@code cdc.stream-key} and tell their entries apart by user, since the shared database gives each class's
     * user an id no other class holds. A class booting a database of its own is the exception — it mints the
     * same low {@code user_id} values another class already published under, so it names a stream key of its
     * own and this method is how it reads that stream whole.
     */
    public static List<ChangeStreamEntry> allEntriesOn(String streamKey) {
        try (RedisClient client = RedisClient.create(RedisContainers.redisUrl())) {
            try (StatefulRedisConnection<String, String> connection = client.connect()) {
                RedisCommands<String, String> commands = connection.sync();
                List<StreamMessage<String, String>> messages = commands.xrange(streamKey, Range.create("-", "+"));
                return messages.stream().map(ChangeStreamEntries::toEntry).toList();
            }
        }
    }

    /**
     * Entries on one named stream naming {@code type} in the event's own type and {@code userId} in its payload's
     * {@code userId}.
     */
    public static List<ChangeStreamEntry> entriesOnFor(String streamKey, String type, long userId) {
        return allEntriesOn(streamKey).stream()
                .filter(entry -> type.equals(entry.type()) && userId == entry.userId())
                .toList();
    }

    private static ChangeStreamEntry toEntry(StreamMessage<String, String> message) {
        String eventId = message.getBody().get(ID_FIELD);
        String type = message.getBody().get(TYPE_FIELD);
        String occurredAt = message.getBody().get(OCCURRED_AT_FIELD);
        String payloadJson = message.getBody().get(PAYLOAD_FIELD);
        JsonNode payload = payloadJson == null ? null : JsonUtils.readJson(payloadJson);
        return new ChangeStreamEntry(message.getId(), eventId, type, occurredAt, payload);
    }

    public record ChangeStreamEntry(String entryId, String eventId, String type, String occurredAt, JsonNode payload) {

        /** The event's own {@code userId}, off its payload. */
        public long userId() {
            return payload.path("userId").asLong();
        }
    }
}
