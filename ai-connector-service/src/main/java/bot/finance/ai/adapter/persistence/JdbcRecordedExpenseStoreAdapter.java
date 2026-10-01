package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcRecordedExpenseStoreAdapter implements RecordedExpenseStorePort {

    private final RecordedExpenseEntityRepository repository;
    private final Clock clock;

    public JdbcRecordedExpenseStoreAdapter(RecordedExpenseEntityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public void apply(SpendingRow entry, RecordedStatus status, StreamPosition position) {
        try {
            repository.upsertApplied(
                    entry.userId(),
                    entry.incomingMessageId().orElse(null),
                    entry.expenseId(),
                    entry.description(),
                    entry.merchant().orElse(null),
                    entry.amount(),
                    entry.currencyCode().code(),
                    entry.category().id(),
                    entry.category().name(),
                    entry.grouping().id(),
                    entry.grouping().name(),
                    status.name(),
                    position.ms(),
                    position.seq(),
                    clock.instant());
        } catch (DataAccessException e) {
            throw MessageStoreExceptionMapper.toDomain(e, "failed to apply a recorded expense");
        }
    }
}
