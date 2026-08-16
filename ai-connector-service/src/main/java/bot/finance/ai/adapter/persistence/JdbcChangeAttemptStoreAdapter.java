package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.ChangeAttemptStorePort;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcChangeAttemptStoreAdapter implements ChangeAttemptStorePort {

    private final StreamEntryFailureEntityRepository repository;

    public JdbcChangeAttemptStoreAdapter(StreamEntryFailureEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int countFailure(String deliveryId, String error) {
        try {
            return repository.countFailure(deliveryId, error, Instant.now());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to count stream entry failure");
        }
    }

    @Override
    public void clear(String deliveryId) {
        repository.deleteById(deliveryId);
    }
}
