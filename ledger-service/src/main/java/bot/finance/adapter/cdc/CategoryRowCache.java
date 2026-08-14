package bot.finance.adapter.cdc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A bounded store of category rows by id, dropping the least recently used entry once it is full. */
class CategoryRowCache {

    private final Map<Long, Optional<CategoryRow>> entries;

    CategoryRowCache(long maxSize) {
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<CategoryRow>> eldest) {
                return size() > maxSize;
            }
        };
    }

    /**
     * The nesting carries two answers a caller has to tell apart: the outer whether the id is stored at all, the
     * inner whether a row exists under it. Flattened, an id nothing was ever read for reads as an id read and
     * found missing, and the store answers from memory for a row that was never looked up.
     */
    Optional<Optional<CategoryRow>> lookup(long id) {
        return Optional.ofNullable(entries.get(id));
    }

    void put(long id, Optional<CategoryRow> row) {
        entries.put(id, row);
    }

    void remove(long id) {
        entries.remove(id);
    }
}
