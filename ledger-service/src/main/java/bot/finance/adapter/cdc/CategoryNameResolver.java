package bot.finance.adapter.cdc;

import bot.finance.adapter.persistence.CategoryRowReader;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers a category's own name and its grouping's from {@link CategoryRowCache}, falling back to
 * {@link CategoryRowReader} for a row the cache does not hold. A grouping is a row like any other, so its name
 * is stored once rather than copied into every entry under it.
 */
@Component
public class CategoryNameResolver {

    private final CategoryRowReader categoryRowReader;
    private final ChangeStreamMeters meters;
    private final CategoryRowCache cache;

    public CategoryNameResolver(
            CategoryRowReader categoryRowReader, ChangeStreamMeters meters, CdcProperties properties) {
        this.categoryRowReader = categoryRowReader;
        this.meters = meters;
        this.cache = new CategoryRowCache(properties.categoryCacheSize());
    }

    public synchronized Optional<CategoryNames> resolve(long categoryId) {
        Optional<Optional<CategoryRow>> cachedCategory = cache.lookup(categoryId);
        Optional<CategoryRow> category = cachedCategory.orElseGet(() -> read(categoryId));

        Optional<Long> groupingId = category.flatMap(CategoryRow::parentId);
        if (groupingId.isEmpty()) {
            countLookup(cachedCategory.isPresent());
            return Optional.empty();
        }

        Optional<Optional<CategoryRow>> cachedGrouping = cache.lookup(groupingId.get());
        Optional<CategoryRow> grouping = cachedGrouping.orElseGet(() -> read(groupingId.get()));
        countLookup(cachedCategory.isPresent() && cachedGrouping.isPresent());

        return grouping.map(found -> new CategoryNames(category.get().name(), found.name()));
    }

    public synchronized void evict(long categoryId) {
        cache.remove(categoryId);
    }

    private Optional<CategoryRow> read(long id) {
        Optional<CategoryRow> found;
        try {
            found = categoryRowReader.findRow(id);
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
