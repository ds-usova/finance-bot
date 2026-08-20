package bot.finance.common.boot;

import bot.finance.adapter.metrics.MicrometerToolCallMeters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves {@link MetricsAdapterTest} itself boots a working context - compiling says nothing about whether the
 * actuator, the {@code PrometheusMeterRegistry} and the security chain that lets a scrape through come up
 * together without the rest of the application. Throwaway: it asserts nothing beyond the autowiring succeeding.
 */
@MetricsAdapterTest
class MetricsAdapterContextTest {

    @Autowired
    private MicrometerToolCallMeters micrometerToolCallMeters;

    @Test
    void contextLoads() {}
}
