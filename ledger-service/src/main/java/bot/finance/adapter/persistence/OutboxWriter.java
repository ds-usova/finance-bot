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
 * Renders each row into a fact, writes it, and clears it again.
 *
 * <p>{@link Propagation#NESTED} puts the three inside a savepoint of the caller's transaction. A statement
 * Postgres refuses aborts everything after it, so without one a failed insert would take the write that produced
 * it down too; rolling back to the savepoint leaves the caller's transaction able to commit. The savepoint is
 * still inside that transaction, so a fact cannot outlive a change that rolled back.
 */
@Component
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final SpendingEventRenderer spendingEventRenderer;

    public OutboxWriter(JdbcTemplate jdbcTemplate, SpendingEventRenderer spendingEventRenderer) {
        this.jdbcTemplate = jdbcTemplate;
        this.spendingEventRenderer = spendingEventRenderer;
    }

    @Transactional(propagation = Propagation.NESTED)
    void write(LedgerEventType type, List<SpendingRowProjection> rows, Instant occurredAt) {
        List<LedgerEvent> events = rows.stream()
                .map(row -> spendingEventRenderer.render(type, row, occurredAt))
                .toList();
        insert(events);
        delete(events.stream().map(LedgerEvent::id).toList());
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
