package bot.finance.adapter.persistence;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.model.WrittenMessage;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("written_message")
public record WrittenMessageEntity(
        @Id Long id, Long userId, String text, String status, String outcome, Instant createdAt, Instant updatedAt) {

    public WrittenMessageView toView() {
        return new WrittenMessageView(
                id,
                userId,
                text,
                WrittenMessageStatus.valueOf(status),
                Optional.ofNullable(outcome).map(ReportOutcome::valueOf));
    }

    public static WrittenMessageEntity fromDomain(WrittenMessage message) {
        return new WrittenMessageEntity(
                message.id().orElse(null),
                message.userId(),
                message.text(),
                message.status().name(),
                null,
                message.createdAt(),
                message.createdAt());
    }
}
