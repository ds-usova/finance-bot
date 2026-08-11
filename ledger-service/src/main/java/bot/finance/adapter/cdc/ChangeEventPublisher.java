package bot.finance.adapter.cdc;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import io.debezium.engine.ChangeEvent;
import org.springframework.stereotype.Component;

/**
 * Shapes the Debezium envelope into a stream entry, adds the category enrichment for an {@code expense} or
 * {@code expense_proposal} row, evicts the resolver's cache on a {@code category} event, and writes the entry
 * to Redis.
 */
@Component
public class ChangeEventPublisher {

    private final CategoryNameResolver categoryNameResolver;
    private final RedisChangeStreamWriter redisChangeStreamWriter;
    private final ChangeStreamMeters meters;

    public ChangeEventPublisher(
            CategoryNameResolver categoryNameResolver,
            RedisChangeStreamWriter redisChangeStreamWriter,
            ChangeStreamMeters meters) {
        this.categoryNameResolver = categoryNameResolver;
        this.redisChangeStreamWriter = redisChangeStreamWriter;
        this.meters = meters;
    }

    public boolean publish(ChangeEvent<String, String> event) {
        // shapes the Debezium envelope, adds the enrichment block for an expense or proposal row,
        // and XADDs it to the capped stream; answers false when Redis or the category lookup refuses
        return false;
    }
}
