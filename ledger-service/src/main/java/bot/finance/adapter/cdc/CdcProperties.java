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
        String recoverySecret) {

    /**
     * An unset environment variable binds as the empty string rather than as null, since the binder ignores a
     * placeholder it cannot resolve — so a blank secret is what "not configured" looks like.
     */
    public CdcProperties {
        if (recoverySecret == null || recoverySecret.isBlank()) {
            throw new IllegalStateException(
                    """
                    cdc.recovery-secret is blank: set CDC_RECOVERY_SECRET, which the slot recovery operation \
                    demands as a header""");
        }
    }
}
