package bot.finance.common.fixtures;

import bot.finance.adapter.cdc.CdcProperties;
import java.time.Duration;

/**
 * {@link CdcProperties} instances for a test that builds a capture component itself rather than autowiring one.
 * Each factory names only what its caller asserts on; everything else takes a value the scenario never reaches.
 */
public final class CdcConfigurations {

    private static final long STREAM_MAX_LENGTH = 1000;
    private static final long CATEGORY_CACHE_SIZE = 100;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration SLOT_MONITOR_INTERVAL = Duration.ofSeconds(10);

    private CdcConfigurations() {}

    /** For a scenario about the slot, whose stream nothing reads. */
    public static CdcProperties forSlot(String slotName) {
        return forStream(slotName, slotName.replace('_', '-') + ".cdc", STREAM_MAX_LENGTH);
    }

    /** For a scenario about the stream, where the key and the cap are what it asserts on. */
    public static CdcProperties forStream(String slotName, String streamKey, long streamMaxLength) {
        return new CdcProperties(
                true,
                slotName,
                streamKey,
                streamMaxLength,
                "never",
                HEARTBEAT_INTERVAL,
                SLOT_MONITOR_INTERVAL,
                CATEGORY_CACHE_SIZE,
                "unused-recovery-secret");
    }
}
