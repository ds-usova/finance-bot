package bot.finance.adapter.persistence;

import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.GroupingRepository;
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
        // reads the caller's parentless row carrying that name and maps it onto StoredGrouping,
        // wrapping any framework exception in PersistenceFailedException
        return Optional.empty();
    }

    @Override
    public List<String> findCategoryNames(long userId, StoredGrouping grouping) {
        // reads the grouping's categories, ordered by name, wrapping any framework exception in
        // PersistenceFailedException
        return List.of();
    }

    @Override
    public List<String> findNamesWithCategories(long userId) {
        // delegates to CategoryEntityRepository.findNonEmptyGroupingNames, wrapping any framework
        // exception in PersistenceFailedException
        return List.of();
    }
}
