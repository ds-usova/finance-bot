package bot.finance.application.port;

import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByExternalId(String externalId);

    User create(User user, List<Category> categories);

}
