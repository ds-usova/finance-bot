package bot.finance.adapter.persistence;

public record PendingCountProjection(String incomingMessageId, long pendingCount) {}
