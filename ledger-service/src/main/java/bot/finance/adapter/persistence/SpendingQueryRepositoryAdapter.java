package bot.finance.adapter.persistence;

import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SpendingQueryRepositoryAdapter implements SpendingQueryRepository {

    private static final String USER_FOREIGN_KEY = "spending_query_user_id_fkey";

    private final SpendingQueryEntityRepository spendingQueryEntityRepository;

    public SpendingQueryRepositoryAdapter(SpendingQueryEntityRepository spendingQueryEntityRepository) {
        this.spendingQueryEntityRepository = spendingQueryEntityRepository;
    }

    @Override
    @Transactional
    public SpendingQuery create(SpendingQuery query) {
        SpendingQueryEntity saved;
        try {
            saved = spendingQueryEntityRepository.save(SpendingQueryEntity.fromDomain(query));
        } catch (RuntimeException e) {
            throw classify(query, e);
        }
        return saved.toDomain();
    }

    @Override
    public List<SpendingPeriod> findPeriodsByMessageReference(long userId, MessageReference reference) {
        try {
            return spendingQueryEntityRepository.findPeriodsByMessageReference(userId, reference.value()).stream()
                    .sorted(Comparator.comparing(SpendingPeriodProjection::createdAt))
                    .map(SpendingPeriodProjection::toPeriod)
                    .toList();
        } catch (RuntimeException e) {
            throw new PersistenceFailedException(
                    "failed to find spending query periods for user " + userId + " and message reference "
                            + reference.value(),
                    e);
        }
    }

    private static RuntimeException classify(SpendingQuery query, RuntimeException e) {
        return switch (ForeignKeyViolations.constraintName(e)) {
            case USER_FOREIGN_KEY -> new EntityNotFoundException("user", "no user stored for id " + query.userId());
            case null, default ->
                new PersistenceFailedException("failed to store spending query for user " + query.userId(), e);
        };
    }
}
