package bot.finance.application.dto;

import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.time.Instant;
import java.util.Optional;

public record WrittenMessageView(
        long id,
        long userId,
        String text,
        WrittenMessageStatus status,
        Optional<ReportOutcome> outcome,
        Instant filedAt) {

    public IncomingMessageId incomingMessageId() {
        return IncomingMessageId.ofWrittenMessage(id);
    }
}
