package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcRecordedExpenseStoreAdapter implements RecordedExpenseStorePort {

    private final RecordedExpenseEntityRepository repository;
    private final IncomingMessageEntityRepository messageRepository;

    public JdbcRecordedExpenseStoreAdapter(
            RecordedExpenseEntityRepository repository, IncomingMessageEntityRepository messageRepository) {
        this.repository = repository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void apply(SpendingRow entry, RecordedStatus status, StreamPosition position) {
        try {
            // Intent: the guarded upsert - insert the row joined to incoming_message on
            // (userId, incomingMessageId); on conflict on expense_id, overwrite every content column,
            // the status and the position, only where the stored position is older than this one.
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to apply a recorded expense");
        }
    }
}
