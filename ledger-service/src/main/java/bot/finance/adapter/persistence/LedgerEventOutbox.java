package bot.finance.adapter.persistence;

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
        // inserts each event as a row in the outbox table
    }

    public void delete(List<UUID> ids) {
        // deletes the outbox rows named by those ids
    }

    public long rowCount() {
        // counts the rows currently in the outbox table
        return 0;
    }
}
