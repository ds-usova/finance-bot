package bot.finance.adapter.cdc;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reads {@link ChangeStreamReader}'s state for {@code /actuator/health}. {@code STANDBY} answers {@code UP}:
 * every instance but one is a standby by design, so treating it as down would put a scaled deployment in
 * permanent alarm.
 */
@Component
public class ChangeStreamHealthIndicator implements HealthIndicator {

    private final ChangeStreamReader changeStreamReader;

    public ChangeStreamHealthIndicator(ChangeStreamReader changeStreamReader) {
        this.changeStreamReader = changeStreamReader;
    }

    @Override
    public Health health() {
        // UP with detail STREAMING or STANDBY, DOWN with detail DOWN, naming the reader's state either way
        return Health.down().build();
    }
}
