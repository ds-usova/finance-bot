package bot.finance.ai.adapter.ledger;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger.turn")
public record LedgerTurnProperties(String target, Duration timeout) {

    public LedgerTurnProperties {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("LEDGER_TURN_TARGET must be set");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("LEDGER_TURN_TIMEOUT must be positive, but was " + timeout);
        }
    }
}
