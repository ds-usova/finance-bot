package bot.finance.adapter.persistence;

import bot.finance.adapter.cdc.CategoryRow;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The live read {@link bot.finance.adapter.cdc.CategoryNameResolver} falls back to on a cache miss: one category
 * row by its own id, whether it is a grouping or a category filed under one.
 */
@Component
public class CategoryNameReader {

    private final CategoryEntityRepository categoryEntityRepository;

    public CategoryNameReader(CategoryEntityRepository categoryEntityRepository) {
        this.categoryEntityRepository = categoryEntityRepository;
    }

    public Optional<CategoryRow> findRow(long categoryId) {
        try {
            return categoryEntityRepository.findCategoryRow(categoryId).map(CategoryRowProjection::toCategoryRow);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find the category row " + categoryId, e);
        }
    }
}
