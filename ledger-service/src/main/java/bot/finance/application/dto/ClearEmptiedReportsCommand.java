package bot.finance.application.dto;

import bot.finance.domain.value.IncomingMessageId;
import java.util.List;

public record ClearEmptiedReportsCommand(long userId, List<IncomingMessageId> incomingMessageIds) {}
