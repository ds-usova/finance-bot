package bot.finance.adapter.cdc;

import bot.finance.adapter.persistence.CategoryNameReader;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final Map<Long, Optional<CategoryNames>> cache;

    public CategoryNameResolver(
            CategoryNameReader categoryNameReader, ChangeStreamMeters meters, CdcProperties properties) {
        this.categoryNameReader = categoryNameReader;
        this.meters = meters;
        this.properties = properties;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<CategoryNames>> eldest) {
                return size() > properties.categoryCacheSize();
            }
        };
    }

    public synchronized Optional<CategoryNames> resolve(long categoryId) {
        Optional<CategoryNames> cached = cache.get(categoryId);
        if (cached != null) {
            meters.countCategoryLookupHit();
            return cached;
        }

        meters.countCategoryLookupMiss();
        Optional<CategoryNames> names;
        try {
            names = categoryNameReader.findNames(categoryId);
        } catch (PersistenceFailedException e) {
            meters.countCategoryLookupFailure();
            throw e;
        }
        cache.put(categoryId, names);
        return names;
    }

    public synchronized void evict(long categoryId) {
        // a cached entry names its grouping, not its grouping's id, so there is no way to tell which entries
        // are filed under the evicted id without clearing every one of them
        cache.clear();
    }
}
