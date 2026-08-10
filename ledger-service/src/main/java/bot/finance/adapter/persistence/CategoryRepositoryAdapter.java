package bot.finance.adapter.persistence;

import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.dto.StoredCategory;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.CategoryRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryEntityRepository categoryEntityRepository;

    public CategoryRepositoryAdapter(CategoryEntityRepository categoryEntityRepository) {
        this.categoryEntityRepository = categoryEntityRepository;
    }

    @Override
    public Optional<StoredCategory> findByGroupingAndName(long userId, StoredGrouping grouping, String name) {
        try {
            return categoryEntityRepository
                    .findByUserIdAndParentIdAndName(userId, grouping.id(), name)
                    .map(CategoryEntity::toStoredCategory);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find category for user " + userId + " under grouping " + grouping.name() + " and name "
                            + name,
                    e);
        }
    }

    @Override
    public boolean existsByUserIdAndName(long userId, String name) {
        try {
            return categoryEntityRepository.existsByUserIdAndNameAndParentIdIsNotNull(userId, name);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to check existence of category for user " + userId + " and name " + name, e);
        }
    }

    @Override
    public List<CategoryEntry> findAllForUser(long userId, Long groupingId) {
        try {
            return categoryEntityRepository.findCategoryEntriesForUser(userId, groupingId).stream()
                    .map(CategoryEntryProjection::toCategoryEntry)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find categories for user " + userId, e);
        }
    }

    @Override
    public boolean existsOwnedCategory(long userId, long categoryId) {
        // will run categoryEntityRepository.existsByIdAndUserIdAndParentIdIsNotNull(categoryId, userId), wrapping
        // a store failure as its siblings do
        return false;
    }
}
