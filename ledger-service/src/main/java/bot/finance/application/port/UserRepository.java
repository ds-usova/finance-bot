package bot.finance.application.port;

import java.util.List;
import java.util.Optional;

import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;

public interface UserRepository {

    Optional<User> findByExternalId(String externalId);

    User create(User user, List<Category> categories);

}
