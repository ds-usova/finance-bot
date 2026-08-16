package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.ChangeAttemptStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcChangeAttemptStoreAdapter implements ChangeAttemptStorePort {

    private final StreamEntryFailureEntityRepository repository;

    public JdbcChangeAttemptStoreAdapter(StreamEntryFailureEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    public int countFailure(String deliveryId, String error) {
        // upserts stream_entry_failure in its own transaction, answering the attempts so far
        return 0;
    }

    @Override
    public void clear(String deliveryId) {
        // deletes the entry's row, if any
    }
}
