package bot.finance.common.boot;

import bot.finance.adapter.cdc.CdcProperties;
import bot.finance.adapter.cdc.ChangeStreamReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves {@link CdcCaptureTest} itself boots a working context - compiling says nothing about whether capture
 * switching on, the Redis singleton and the engine's own bean graph actually come up together. Throwaway: it
 * asserts nothing beyond the autowiring succeeding, and beyond {@code cdc.enabled} having actually taken effect.
 */
@CdcCaptureTest
class CdcCaptureContextTest {

    @Autowired
    private ChangeStreamReader changeStreamReader;

    @Autowired
    private CdcProperties cdcProperties;

    @Test
    void contextLoads() {}
}
