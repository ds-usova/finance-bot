package bot.finance.application.port;

import bot.finance.application.dto.StoredCategory;
import bot.finance.application.dto.StoredGrouping;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.Optional;

public interface CategoryRepository {

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    Optional<StoredCategory> findByGroupingAndName(long userId, StoredGrouping grouping, String name);

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    boolean existsByUserIdAndName(long userId, String name);
}
