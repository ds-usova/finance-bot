package bot.finance.application.dto;

import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.WrittenMessageStatus;
import java.util.Optional;

public record WrittenMessageView(
        long id, long userId, String text, WrittenMessageStatus status, Optional<ReportOutcome> outcome) {

    public IncomingMessageId incomingMessageId() {
        return IncomingMessageId.ofWrittenMessage(id);
    }
}
