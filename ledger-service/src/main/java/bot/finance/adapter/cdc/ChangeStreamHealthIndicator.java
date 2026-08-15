package bot.finance.adapter.cdc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Reads {@link ChangeStreamReader}'s state for {@code /actuator/health}. {@code STANDBY} answers {@code UP}:
 * every instance but one is a standby by design, so treating it as down would put a scaled deployment in
 * permanent alarm. Registered only while capture is on: {@code STANDBY}/{@code DOWN} are meaningless for an
 * engine that is never started, and the aggregate must not fall over for a reason capture being off has nothing
 * to do with.
 */
@Component
@ConditionalOnProperty(name = "cdc.enabled", havingValue = "true")
public class ChangeStreamHealthIndicator implements HealthIndicator {

    private final ChangeStreamReader changeStreamReader;

    public ChangeStreamHealthIndicator(ChangeStreamReader changeStreamReader) {
        this.changeStreamReader = changeStreamReader;
    }

    @Override
    public Health health() {
        ChangeStreamState state = changeStreamReader.state();
        Status status = state == ChangeStreamState.DOWN ? Status.DOWN : Status.UP;

        return Health.status(status).withDetail("state", state.name()).build();
    }
}
