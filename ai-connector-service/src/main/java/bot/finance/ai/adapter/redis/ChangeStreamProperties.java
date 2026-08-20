package bot.finance.ai.adapter.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger.change-stream")
public record ChangeStreamProperties(String key, Duration claimIdle) {}
