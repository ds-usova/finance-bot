package bot.finance.common.boot;

import bot.finance.adapter.cdc.CdcProperties;
import bot.finance.adapter.cdc.ChangeStreamReader;
import bot.finance.adapter.cdc.ChangeStreamRecovery;
import bot.finance.adapter.cdc.ReplicationSlotMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Proves {@link CdcAdapterTest} itself boots a working context - compiling says nothing about whether the
 * engine's bean graph, the Redis template and the Data JDBC slice come up together without the rest of the
 * application. Throwaway: it asserts nothing beyond the autowiring succeeding.
 */
@CdcAdapterTest
@TestPropertySource(properties = "cdc.slot-name=cdc_adapter_context_test")
class CdcAdapterContextTest {

    @Autowired
    private ChangeStreamReader changeStreamReader;

    @Autowired
    private ChangeStreamRecovery changeStreamRecovery;

    @Autowired
    private ReplicationSlotMonitor replicationSlotMonitor;

    @Autowired
    private CdcProperties cdcProperties;

    @Test
    void contextLoads() {}
}
