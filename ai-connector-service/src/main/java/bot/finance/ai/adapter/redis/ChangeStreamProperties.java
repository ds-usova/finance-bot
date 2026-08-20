package bot.finance.ai.adapter.redis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger.change-stream")
public record ChangeStreamProperties(String key, Duration claimIdle) {

    // Shared by the consumer and the pending-entry count, both reading the same group off the same stream.
    static final String GROUP = "ai-connector";
}
