package bot.finance.application.port;

import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    /**
     * @throws PersistenceFailedException if the lookup fails
     */
    Optional<User> findByExternalId(String externalId);

    /**
     * @throws InvalidUserException if the user's external id violates a column constraint
     * @throws InvalidCategoryException if a category name violates a column constraint
     * @throws PersistenceFailedException if the write fails
     */
    User create(User user, List<Category> categories);
}
