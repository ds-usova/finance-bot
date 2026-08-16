package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("incoming_message")
public record IncomingMessageEntity(
        @Id Long id, long userId, String incomingMessageId, String text, Instant receivedAt) {}
