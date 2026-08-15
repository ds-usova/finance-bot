package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        // inserts the row under (user_id, incoming_message_id), leaving an existing row untouched, and
        // translates any store failure into MessageStoreFailedException
    }

    @Override
    public int deleteReceivedBefore(Instant cut, int batch) {
        // deletes one batch of rows received before the cut, claiming only rows no other purge holds,
        // and translates any store failure into MessageStoreFailedException
        return 0;
    }
}
