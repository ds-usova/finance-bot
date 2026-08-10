package bot.finance.adapter.persistence;

import bot.finance.domain.value.IncomingMessageId;

public record PendingCountProjection(String incomingMessageId, long pendingCount) {

    public IncomingMessageId toIncomingMessageId() {
        return IncomingMessageId.of(incomingMessageId);
    }
}
