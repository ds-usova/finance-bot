package bot.finance.ai.application.port;

import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;

public interface RecordedExpenseStorePort {

    void apply(SpendingRow entry, RecordedStatus status, StreamPosition position);
}
