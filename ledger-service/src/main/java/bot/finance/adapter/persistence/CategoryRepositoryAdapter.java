package bot.finance.adapter.persistence;

import bot.finance.application.dto.KnownCategory;
import bot.finance.application.dto.StoredCategory;
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
    public List<StoredCategory> findByUserIdAndName(long userId, String name) {
        try {
            return categoryEntityRepository.findByUserIdAndName(userId, name).stream()
                    .map(this::toStoredCategory)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find category " + name + " for user " + userId, e);
        }
    }

    @Override
    public List<String> findChildNames(long categoryId) {
        try {
            return categoryEntityRepository.findByParentId(categoryId).stream()
                    .map(CategoryEntity::name)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find children of category " + categoryId, e);
        }
    }

    @Override
    public List<KnownCategory> findKnownCategories(long userId) {
        try {
            return categoryEntityRepository.findKnownCategories(userId).stream()
                    .map(KnownCategoryProjection::toKnownCategory)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find known categories for user " + userId, e);
        }
    }

    private StoredCategory toStoredCategory(CategoryEntity entity) {
        Optional<String> parentName = Optional.ofNullable(entity.parentId())
                .flatMap(categoryEntityRepository::findById)
                .map(CategoryEntity::name);
        return new StoredCategory(entity.id(), entity.name(), parentName);
    }
}
