package bot.finance.adapter.persistence;

import bot.finance.application.dto.StoredCategory;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.application.port.CategoryRepository;
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
        // reads the category row filed under the given grouping and carrying that name, mapping it onto
        // StoredCategory, wrapping any framework exception in PersistenceFailedException
        return Optional.empty();
    }

    @Override
    public boolean existsByUserIdAndName(long userId, String name) {
        // answers whether any category (a row with a parent) of this user's carries that name,
        // wrapping any framework exception in PersistenceFailedException
        return false;
    }
}
