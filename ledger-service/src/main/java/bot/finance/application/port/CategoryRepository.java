package bot.finance.application.port;

import bot.finance.application.dto.KnownCategory;
import bot.finance.application.dto.StoredCategory;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;

public interface CategoryRepository {

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    List<StoredCategory> findByUserIdAndName(long userId, String name);

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    List<String> findChildNames(long categoryId);

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    List<KnownCategory> findKnownCategories(long userId);
}
