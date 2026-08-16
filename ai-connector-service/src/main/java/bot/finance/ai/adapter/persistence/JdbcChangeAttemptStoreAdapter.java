package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
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
        } catch (DataAccessResourceFailureException | TransientDataAccessException e) {
            throw new MessageStoreUnavailableException("failed to count stream entry failure", e);
        } catch (DataAccessException e) {
            throw new MessageStoreFailedException("failed to count stream entry failure", e);
        }
    }

    @Override
    public void clear(String deliveryId) {
        repository.deleteById(deliveryId);
    }
}
