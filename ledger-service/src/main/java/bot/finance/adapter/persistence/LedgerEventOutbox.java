package bot.finance.adapter.persistence;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.OutboxMeters;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The outbox a spending write records its facts through. Rendering a fact, writing it, and clearing it again are
 * one call: a fact left behind is a row the capture pipeline republishes forever, and a caller that has to
 * remember a second step is a caller that will one day forget it.
 *
 * <p>Publishing is a bonus on top of the write, so a fact that cannot be recorded is logged and dropped rather
 * than failing the change it describes. {@link OutboxWriter} runs inside a savepoint, which is what leaves the
 * caller's transaction able to commit after a refused statement — catching here, outside that boundary, is what
 * makes the rollback to it happen.
 */
@Component
public class LedgerEventOutbox {

    private final OutboxWriter outboxWriter;
    private final OutboxMeters outboxMeters;
    private final JdbcTemplate jdbcTemplate;
    private final Logger log;

    public LedgerEventOutbox(
            OutboxWriter outboxWriter,
            OutboxMeters outboxMeters,
            JdbcTemplate jdbcTemplate,
            LoggerFactory loggerFactory) {
        this.outboxWriter = outboxWriter;
        this.outboxMeters = outboxMeters;
        this.jdbcTemplate = jdbcTemplate;
        this.log = loggerFactory.getLogger(LedgerEventOutbox.class);
    }

    public void append(LedgerEventType type, List<SpendingRowProjection> rows, Instant occurredAt) {
        if (rows.isEmpty()) {
            return;
        }

        try {
            outboxWriter.write(type, rows, occurredAt);
        } catch (RuntimeException e) {
            outboxMeters.countFactsDropped(type.name(), rows.size());
            log.error(
                    "Failed to record {} {} facts, which reach no consumer: {}",
                    rows.size(),
                    type,
                    rows.stream().map(SpendingRowProjection::id).toList(),
                    e);
        }
    }

    public long rowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Long.class);
        return count == null ? 0 : count;
    }
}
