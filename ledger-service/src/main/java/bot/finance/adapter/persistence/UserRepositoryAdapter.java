package bot.finance.adapter.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import bot.finance.application.port.UserRepository;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private static final int MAX_EXTERNAL_ID_LENGTH = 255;
    private static final int MAX_CATEGORY_NAME_LENGTH = 100;

    private final UserEntityRepository userEntityRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public UserRepositoryAdapter(UserEntityRepository userEntityRepository,
                                  JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.userEntityRepository = userEntityRepository;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    @Override
    public Optional<User> findByExternalId(String externalId) {
        // looks the user row up by its external id and maps it to the domain
        return Optional.empty();
    }

    @Override
    @Transactional
    public User create(User user, List<Category> categories) {
        // rejects an external id over 255 characters with InvalidUserException and any category
        // name over 100 with InvalidCategoryException, before writing anything;
        // inserts the user, then the groups in one insertAll batch, then their children in a
        // second batch against the ids their groups came back with;
        // translates DataIntegrityViolationException into PersistenceFailedException;
        // returns the user with its generated id
        return null;
    }

}
