package bot.finance.ai.adapter.ledger;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger.turn")
public record LedgerTurnProperties(String target, Duration timeout) {}
