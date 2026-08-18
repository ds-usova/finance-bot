package bot.finance.adapter.persistence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbox a spending write records its facts through. Rendering a row into a fact, writing it, and clearing it
 * again are one call: a fact left behind is a row the capture pipeline republishes forever, and a caller that has
 * to remember a second step is a caller that will one day forget it.
 */
@Component
public class LedgerEventOutbox {

    private final JdbcTemplate jdbcTemplate;
    private final SpendingEventRenderer spendingEventRenderer;

    public LedgerEventOutbox(JdbcTemplate jdbcTemplate, SpendingEventRenderer spendingEventRenderer) {
        this.jdbcTemplate = jdbcTemplate;
        this.spendingEventRenderer = spendingEventRenderer;
    }

    /**
     * Records one fact per row, then clears them. {@link Propagation#MANDATORY} is the guarantee: the rows are
     * written and cleared inside the caller's own transaction, so a fact cannot commit without the change it
     * describes, and a call outside a transaction is refused rather than committing on its own.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(LedgerEventType type, List<SpendingRowProjection> rows, Instant occurredAt) {
        if (rows.isEmpty()) {
            return;
        }

        List<LedgerEvent> events = rows.stream()
                .map(row -> spendingEventRenderer.render(type, row, occurredAt))
                .toList();
        insert(events);
        delete(events.stream().map(LedgerEvent::id).toList());
    }

    public long rowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox", Long.class);
        return count == null ? 0 : count;
    }

    private void insert(List<LedgerEvent> events) {
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

    private void delete(List<UUID> ids) {
        jdbcTemplate.batchUpdate("DELETE FROM outbox WHERE id = ?", ids, ids.size(), (ps, id) -> ps.setObject(1, id));
    }
}
