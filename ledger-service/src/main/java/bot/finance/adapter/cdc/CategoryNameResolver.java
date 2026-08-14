package bot.finance.adapter.cdc;

import bot.finance.adapter.persistence.CategoryNameReader;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * A bounded LRU cache over {@link CategoryNameReader}, sized by {@link CdcProperties#categoryCacheSize()} and
 * holding one category row per id, groupings included. A category's two names come from its own row and its
 * parent's, so a grouping's name is stored once rather than copied into every entry under it.
 */
@Component
public class CategoryNameResolver {

    private final CategoryNameReader categoryNameReader;
    private final ChangeStreamMeters meters;
    private final CdcProperties properties;
    private final Map<Long, Optional<CategoryRow>> cache;

    public CategoryNameResolver(
            CategoryNameReader categoryNameReader, ChangeStreamMeters meters, CdcProperties properties) {
        this.categoryNameReader = categoryNameReader;
        this.meters = meters;
        this.properties = properties;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<CategoryRow>> eldest) {
                return size() > properties.categoryCacheSize();
            }
        };
    }

    public synchronized Optional<CategoryNames> resolve(long categoryId) {
        boolean fromMemory = cache.containsKey(categoryId);

        Optional<CategoryRow> category = row(categoryId);
        Optional<Long> groupingId = category.flatMap(CategoryRow::parentId);
        if (groupingId.isEmpty()) {
            countLookup(fromMemory);
            return Optional.empty();
        }

        fromMemory = fromMemory && cache.containsKey(groupingId.get());
        Optional<CategoryRow> grouping = row(groupingId.get());
        countLookup(fromMemory);

        return grouping.map(found -> new CategoryNames(category.get().name(), found.name()));
    }

    public synchronized void evict(long categoryId) {
        cache.remove(categoryId);
    }

    private Optional<CategoryRow> row(long id) {
        Optional<CategoryRow> cached = cache.get(id);
        if (cached != null) {
            return cached;
        }

        Optional<CategoryRow> found;
        try {
            found = categoryNameReader.findRow(id);
        } catch (PersistenceFailedException e) {
            meters.countCategoryLookupFailure();
            throw e;
        }
        cache.put(id, found);
        return found;
    }

    private void countLookup(boolean fromMemory) {
        if (fromMemory) {
            meters.countCategoryLookupHit();
        } else {
            meters.countCategoryLookupMiss();
        }
    }
}
