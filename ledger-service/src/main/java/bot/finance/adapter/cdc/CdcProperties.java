package bot.finance.adapter.cdc;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cdc")
public record CdcProperties(
        boolean enabled,
        String slotName,
        String streamKey,
        long streamMaxLength,
        String snapshotMode,
        Duration heartbeatInterval,
        Duration slotMonitorInterval,
        long categoryCacheSize,
        String recoverySecret) {}
