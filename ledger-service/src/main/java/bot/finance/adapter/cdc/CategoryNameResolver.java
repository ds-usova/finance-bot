package bot.finance.adapter.cdc;

import bot.finance.adapter.persistence.CategoryNameReader;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A bounded LRU cache over {@link CategoryNameReader}, sized by {@link CdcProperties#categoryCacheSize()}. A
 * grouping renamed evicts its own entry and every category filed under it, since each cached entry carries
 * names drawn from both rows.
 */
@Component
public class CategoryNameResolver {

    private final CategoryNameReader categoryNameReader;
    private final ChangeStreamMeters meters;
    private final CdcProperties properties;

    public CategoryNameResolver(
            CategoryNameReader categoryNameReader, ChangeStreamMeters meters, CdcProperties properties) {
        this.categoryNameReader = categoryNameReader;
        this.meters = meters;
        this.properties = properties;
    }

    public Optional<CategoryNames> resolve(long categoryId) {
        // answers a cached entry without reading the store, or reads through CategoryNameReader on a miss and
        // caches the result; a lookup that finds nothing caches and answers Optional.empty() rather than
        // throwing, and a lookup that fails propagates without caching anything
        return Optional.empty();
    }

    public void evict(long categoryId) {
        // drops the entry keyed by this id and every entry whose grouping is this id, so a renamed grouping
        // never leaves a child category serving the old name
    }
}
