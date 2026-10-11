package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.ChangeAttemptStorePort;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcChangeAttemptStoreAdapter implements ChangeAttemptStorePort {

    private final StreamEntryFailureEntityRepository repository;
    private final Clock clock;

    public JdbcChangeAttemptStoreAdapter(StreamEntryFailureEntityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int countFailure(String deliveryId, String error) {
        try {
            return repository.countFailure(deliveryId, error, clock.instant());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to count stream entry failure");
        }
    }

    @Override
    public void clear(String deliveryId) {
        repository.deleteById(deliveryId);
    }
}
