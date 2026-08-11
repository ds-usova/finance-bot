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

    private static final String STREAM_KEY = "ledger.cdc";
    private static final String PAYLOAD_FIELD = "payload";
    private static final String ENRICHMENT_FIELD = "enrichment";

    private ChangeStreamEntries() {}

    /** Every entry on the stream, in the order they were XADDed. */
    public static List<ChangeStreamEntry> allEntries() {
        try (RedisClient client = RedisClient.create(RedisContainers.redisUrl())) {
            try (StatefulRedisConnection<String, String> connection = client.connect()) {
                RedisCommands<String, String> commands = connection.sync();
                List<StreamMessage<String, String>> messages = commands.xrange(STREAM_KEY, Range.create("-", "+"));
                return messages.stream().map(ChangeStreamEntries::toEntry).toList();
            }
        }
    }

    /**
     * Entries naming {@code table} in {@code source.table} and {@code userId} in the row's own {@code user_id} -
     * read from {@code after} where present, {@code before} otherwise, since a delete carries no {@code after}.
     */
    public static List<ChangeStreamEntry> entriesFor(String table, long userId) {
        return allEntries().stream()
                .filter(entry -> table.equals(entry.table()) && userId == entry.userId())
                .toList();
    }

    private static ChangeStreamEntry toEntry(StreamMessage<String, String> message) {
        String payloadJson = message.getBody().get(PAYLOAD_FIELD);
        String enrichmentJson = message.getBody().get(ENRICHMENT_FIELD);
        JsonNode payload = payloadJson == null ? null : JsonUtils.readJson(payloadJson);
        JsonNode enrichment = enrichmentJson == null ? null : JsonUtils.readJson(enrichmentJson);
        return new ChangeStreamEntry(message.getId(), payload, enrichment);
    }

    public record ChangeStreamEntry(String entryId, JsonNode payload, JsonNode enrichment) {

        public String op() {
            return payload.path("op").asText();
        }

        public JsonNode source() {
            return payload.path("source");
        }

        public String table() {
            return source().path("table").asText();
        }

        public JsonNode after() {
            return payload.path("after");
        }

        public JsonNode before() {
            return payload.path("before");
        }

        /** The row's own {@code user_id} - {@link #after()} where present, {@link #before()} otherwise. */
        public long userId() {
            JsonNode row = after().isMissingNode() || after().isNull() ? before() : after();
            return row.path("user_id").asLong();
        }
    }
}
