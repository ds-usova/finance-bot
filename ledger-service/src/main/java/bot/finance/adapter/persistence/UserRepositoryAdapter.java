package bot.finance.adapter.persistence;

import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserEntityRepository userEntityRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public UserRepositoryAdapter(UserEntityRepository userEntityRepository,
                                 JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.userEntityRepository = userEntityRepository;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    @Override
    public Optional<User> findByExternalId(String externalId) {
        return userEntityRepository.findByExternalId(externalId).map(UserEntity::toDomain);
    }

    @Override
    @Transactional
    public User create(User user, List<Category> categories) {
        ColumnLimits.validateExternalId(user.externalId());
        ColumnLimits.validateCategoryNames(categories);

        try {
            return insert(user, categories);
        } catch (DataIntegrityViolationException e) {
            throw new PersistenceFailedException("failed to store user " + user.externalId(), e);
        }
    }

    private User insert(User user, List<Category> categories) {
        UserEntity storedUser = userEntityRepository.save(UserEntity.fromDomain(user));
        long userId = storedUser.id();

        List<CategoryEntity> groupEntities = categories.stream()
                .map(group -> CategoryEntity.root(userId, group))
                .toList();
        List<CategoryEntity> storedGroups = jdbcAggregateTemplate.insertAll(groupEntities);

        // JdbcAggregateTemplate.insertAll returns the stored entities in the same order it was
        // given them (verified against spring-data-jdbc-4.1.0: insertAll -> doInBatch ->
        // performSaveAll, and JdbcAggregateChangeExecutionContext.populateIdsIfNecessary()
        // re-reverses its internal list before returning), so the group at index i pairs with
        // the generated id at index i.
        List<CategoryEntity> childEntities = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            Category group = categories.get(i);
            long groupId = storedGroups.get(i).id();
            for (Category child : group.children()) {
                childEntities.add(CategoryEntity.child(userId, groupId, child));
            }
        }
        if (!childEntities.isEmpty()) {
            jdbcAggregateTemplate.insertAll(childEntities);
        }

        return storedUser.toDomain();
    }

}
