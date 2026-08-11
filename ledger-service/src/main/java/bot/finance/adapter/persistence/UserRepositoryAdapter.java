package bot.finance.adapter.persistence;

import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Category;
import bot.finance.domain.value.Grouping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserEntityRepository userEntityRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;

    public UserRepositoryAdapter(
            UserEntityRepository userEntityRepository, JdbcAggregateTemplate jdbcAggregateTemplate) {
        this.userEntityRepository = userEntityRepository;
        this.jdbcAggregateTemplate = jdbcAggregateTemplate;
    }

    @Override
    public Optional<User> findByExternalId(String externalId) {
        try {
            return userEntityRepository.findByExternalId(externalId).map(UserEntity::toDomain);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find user " + externalId, e);
        }
    }

    @Override
    public Optional<User> findById(long userId) {
        try {
            return userEntityRepository.findById(userId).map(UserEntity::toDomain);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to find user " + userId, e);
        }
    }

    @Override
    @Transactional
    public User create(User user, List<Grouping> groupings) {
        ColumnLimits.validateExternalId(user.externalId());
        ColumnLimits.validateCatalogueNames(groupings);

        try {
            return insertOrFindExisting(user, groupings);
        } catch (RuntimeException e) {
            throw new PersistenceFailedException("failed to store user " + user.externalId(), e);
        }
    }

    private User insertOrFindExisting(User user, List<Grouping> groupings) {
        // A concurrent uncommitted insert for the same external id makes this statement wait
        // rather than conflict, so the follow-up read below runs only after that insert commits.
        Optional<Long> insertedId = userEntityRepository.insertIfAbsent(user.externalId());
        if (insertedId.isEmpty()) {
            // READ COMMITTED (Postgres' default, unchanged here) takes a fresh snapshot for this
            // statement, so it is guaranteed to see the row the other caller just committed.
            return userEntityRepository
                    .findByExternalId(user.externalId())
                    .map(UserEntity::toDomain)
                    .orElseThrow(
                            () -> new PersistenceFailedException("failed to store user " + user.externalId(), null));
        }

        long userId = insertedId.get();
        writeCategoryTree(userId, groupings);
        return User.stored(userId, user.externalId());
    }

    private void writeCategoryTree(long userId, List<Grouping> groupings) {
        List<CategoryEntity> groupingEntities = groupings.stream()
                .map(grouping -> CategoryEntity.grouping(userId, grouping))
                .toList();
        List<CategoryEntity> storedGroupings = jdbcAggregateTemplate.insertAll(groupingEntities);
        Map<String, Long> groupingIdByName =
                storedGroupings.stream().collect(Collectors.toMap(CategoryEntity::name, CategoryEntity::id));

        List<CategoryEntity> categoryEntities = new ArrayList<>();
        for (Grouping grouping : groupings) {
            long groupingId = groupingIdByName.get(grouping.name());
            for (Category category : grouping.categories()) {
                categoryEntities.add(CategoryEntity.category(userId, groupingId, category));
            }
        }
        if (!categoryEntities.isEmpty()) {
            jdbcAggregateTemplate.insertAll(categoryEntities);
        }
    }
}
