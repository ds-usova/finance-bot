package bot.finance.adapter.cdc;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import bot.finance.domain.exception.PersistenceFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.debezium.engine.ChangeEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Shapes the Debezium envelope into a stream entry, adds the category enrichment for an {@code expense} row,
 * hands the resolver the row a {@code category} event carries, and writes the entry to Redis.
 */
@Component
public class ChangeEventPublisher {

    private static final String CATEGORY_TABLE = "category";

    private final CategoryNameResolver categoryNameResolver;
    private final RedisChangeStreamWriter redisChangeStreamWriter;
    private final ChangeStreamMeters meters;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChangeEventPublisher(
            CategoryNameResolver categoryNameResolver,
            RedisChangeStreamWriter redisChangeStreamWriter,
            ChangeStreamMeters meters) {
        this.categoryNameResolver = categoryNameResolver;
        this.redisChangeStreamWriter = redisChangeStreamWriter;
        this.meters = meters;
    }

    public boolean publish(ChangeEvent<String, String> event) {
        String payload = event.value();
        JsonNode root = readPayload(payload);
        String table = root.path("source").path("table").asText();
        String operation = root.path("op").asText();

        Optional<String> enrichment;
        if (CATEGORY_TABLE.equals(table)) {
            applyCategoryChange(root);
            enrichment = Optional.empty();
        } else {
            try {
                enrichment = Optional.of(buildEnrichment(root));
            } catch (PersistenceFailedException e) {
                return false;
            }
        }

        if (!redisChangeStreamWriter.write(payload, enrichment)) {
            meters.countPublishFailure();
            return false;
        }

        meters.countPublished(table, operation);
        meters.setEventLag(
                Instant.ofEpochMilli(root.path("source").path("ts_ms").asLong()));
        return true;
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse change event payload", e);
        }
    }

    /**
     * The row travels on the event under {@code REPLICA IDENTITY FULL}, so an insert or an update hands the
     * resolver what it would otherwise read back. A delete has no {@code after} side and leaves nothing to store.
     */
    private void applyCategoryChange(JsonNode root) {
        JsonNode after = root.path("after");
        if (after.isMissingNode() || after.isNull()) {
            categoryNameResolver.evict(root.path("before").path("id").asLong());
            return;
        }

        categoryNameResolver.refresh(after.path("id").asLong(), categoryRow(after));
    }

    private CategoryRow categoryRow(JsonNode side) {
        JsonNode parentId = side.path("parent_id");
        return new CategoryRow(
                side.path("name").asText(),
                parentId.isMissingNode() || parentId.isNull() ? Optional.empty() : Optional.of(parentId.asLong()));
    }

    private String buildEnrichment(JsonNode root) {
        ObjectNode enrichment = objectMapper.createObjectNode();
        JsonNode before = root.path("before");
        if (!before.isMissingNode()) {
            enrichment.set("before", enrichedSide(before));
        }
        JsonNode after = root.path("after");
        if (!after.isMissingNode()) {
            enrichment.set("after", enrichedSide(after));
        }
        return enrichment.toString();
    }

    private ObjectNode enrichedSide(JsonNode side) {
        ObjectNode sideEnrichment = objectMapper.createObjectNode();
        long categoryId = side.path("category_id").asLong();
        categoryNameResolver.resolve(categoryId).ifPresent(names -> {
            sideEnrichment.put("categoryName", names.categoryName());
            sideEnrichment.put("groupingName", names.groupingName());
        });
        return sideEnrichment;
    }
}
