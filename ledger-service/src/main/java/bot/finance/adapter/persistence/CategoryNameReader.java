package bot.finance.adapter.persistence;

import bot.finance.adapter.cdc.CategoryNames;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The live read {@link bot.finance.adapter.cdc.CategoryNameResolver} falls back to on a cache miss: a
 * category's own name and its grouping's, read together by the category's id alone.
 */
@Component
public class CategoryNameReader {

    private final CategoryEntityRepository categoryEntityRepository;

    public CategoryNameReader(CategoryEntityRepository categoryEntityRepository) {
        this.categoryEntityRepository = categoryEntityRepository;
    }

    public Optional<CategoryNames> findNames(long categoryId) {
        return categoryEntityRepository.findCategoryNames(categoryId).map(CategoryNamesProjection::toCategoryNames);
    }
}
