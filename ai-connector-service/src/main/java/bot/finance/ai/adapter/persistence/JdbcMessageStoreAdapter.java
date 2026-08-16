package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcMessageStoreAdapter implements MessageStorePort {

    private final IncomingMessageEntityRepository repository;

    public JdbcMessageStoreAdapter(IncomingMessageEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public void register(MessageIdentity identity, String text) {
        try {
            repository.insertIgnoringConflict(identity.userId(), identity.incomingMessageId(), text);
        } catch (DataAccessException e) {
            throw new MessageStoreFailedException("failed to register incoming message", e);
        }
    }

    @Override
    public int deleteReceivedBefore(Instant cut, int batch) {
        try {
            return repository.deleteReceivedBefore(cut, batch);
        } catch (DataAccessException e) {
            throw new MessageStoreFailedException("failed to delete incoming messages", e);
        }
    }
}
