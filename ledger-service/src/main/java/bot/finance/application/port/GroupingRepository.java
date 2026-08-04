package bot.finance.application.port;

import bot.finance.application.dto.StoredGrouping;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;
import java.util.Optional;

public interface GroupingRepository {

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    Optional<StoredGrouping> findByUserIdAndName(long userId, String name);

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    List<String> findCategoryNames(long userId, StoredGrouping grouping);

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    List<String> findNamesWithCategories(long userId);
}
