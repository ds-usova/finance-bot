package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.UUID;

public record LedgerEvent(UUID id, String type, Instant occurredAt, String payload) {}
