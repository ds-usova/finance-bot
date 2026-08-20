package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("stream_entry_failure")
public record StreamEntryFailureEntity(@Id String entryId, int attempts, Instant firstFailedAt, String lastError) {}
