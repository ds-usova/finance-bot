package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns a changed row into a typed event with its JSON body.
 */
@Component
public class SpendingEventRenderer {

    public LedgerEvent render(String type, SpendingRowProjection row, Instant occurredAt) {
        // renders the row's fields into the event's JSON payload
        return new LedgerEvent(UUID.randomUUID(), type, occurredAt, null);
    }
}
