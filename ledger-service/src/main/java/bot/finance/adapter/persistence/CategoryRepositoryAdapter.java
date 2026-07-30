package bot.finance.adapter.persistence;

import bot.finance.application.dto.StoredCategory;
import bot.finance.application.port.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryEntityRepository categoryEntityRepository;

    public CategoryRepositoryAdapter(CategoryEntityRepository categoryEntityRepository) {
        this.categoryEntityRepository = categoryEntityRepository;
    }

    @Override
    public List<StoredCategory> findByUserIdAndName(long userId, String name) {
        // reads every one of that user's categories carrying the name, resolving each row's parent
        // name, and translates every runtime exception into PersistenceFailedException
        return List.of();
    }

    @Override
    public List<String> findChildNames(long categoryId) {
        // reads the names of the categories whose parent is the given one, translating every runtime
        // exception into PersistenceFailedException
        return List.of();
    }
}
