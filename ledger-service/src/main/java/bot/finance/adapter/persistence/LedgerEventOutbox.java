package bot.finance.adapter.persistence;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inserts the events, deletes them again, counts what is left.
 */
@Component
public class LedgerEventOutbox {

    private final JdbcTemplate jdbcTemplate;

    public LedgerEventOutbox(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(List<LedgerEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO outbox (id, type, occurred_at, payload) VALUES (?, ?, ?, ?::jsonb)",
                events,
                events.size(),
                (ps, event) -> {
                    ps.setObject(1, event.id());
                    ps.setString(2, event.type().name());
                    ps.setObject(3, event.occurredAt().atOffset(ZoneOffset.UTC));
                    ps.setString(4, event.payload());
                });
    }

    public void delete(List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate("DELETE FROM outbox WHERE id = ?", ids, ids.size(), (ps, id) -> ps.setObject(1, id));
    }

    public long rowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Long.class);
        return count == null ? 0 : count;
    }
}
