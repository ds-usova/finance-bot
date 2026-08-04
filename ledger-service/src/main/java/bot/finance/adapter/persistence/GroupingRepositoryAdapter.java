package bot.finance.adapter.persistence;

import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.GroupingRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class GroupingRepositoryAdapter implements GroupingRepository {

    private final CategoryEntityRepository categoryEntityRepository;

    public GroupingRepositoryAdapter(CategoryEntityRepository categoryEntityRepository) {
        this.categoryEntityRepository = categoryEntityRepository;
    }

    @Override
    public Optional<StoredGrouping> findByUserIdAndName(long userId, String name) {
        try {
            return categoryEntityRepository
                    .findByUserIdAndNameAndParentIdIsNull(userId, name)
                    .map(CategoryEntity::toStoredGrouping);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find grouping for user " + userId, e);
        }
    }

    @Override
    public List<String> findCategoryNames(long userId, StoredGrouping grouping) {
        try {
            return categoryEntityRepository.findByUserIdAndParentIdOrderByName(userId, grouping.id()).stream()
                    .map(CategoryEntity::name)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find category names for user " + userId, e);
        }
    }

    @Override
    public List<String> findNamesWithCategories(long userId) {
        try {
            return categoryEntityRepository.findNonEmptyGroupingNames(userId);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find grouping names for user " + userId, e);
        }
    }
}
